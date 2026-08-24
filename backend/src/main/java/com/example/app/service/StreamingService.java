package com.example.app.service;

import com.example.app.config.StreamingProperties;
import com.example.app.dto.ChatRequest;
import com.example.app.pipeline.ContextPipelineExecutor;
import com.example.app.pipeline.PipelineEntryDispatcher;
import com.example.app.pipeline.context.ConversationContext;
import com.example.app.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.CompletableFuture;

/**
 * 流式响应服务，处理 SSE 流式消息传输。
 *
 * <p>超时对齐：SSE emitter 超时取自 {@link StreamingProperties#getSseTimeoutMs()}，
 * 严格大于 Agent 流式 LLM 等待上限 {@link StreamingProperties#getAgentStreamingTimeoutMs()}，
 * 保证 LLM 还在响应时 emitter 不会先超时关闭（详见 {@code StreamingProperties} 启动校验）。
 *
 * <p>线程池：异步任务跑在专用的 {@code streamingTaskExecutor}（core=20/max=50/queue=100），
 * 不再走 {@code ForkJoinPool.commonPool()}，避免 LLM 长任务耗尽 commonPool 影响其他业务。
 *
 * <p>客户端断连处理：emitter 的 onTimeout / onError 回调里设置
 * {@code ctx.clientCancelled}，LLM 回调线程检测后提前 countDown，
 * 避免继续等满 10min；后端循环检测后提前 break，节省 token 成本。
 *
 * 执行流程已迁移到 Context Pipeline：
 * 1. 创建 SseEmitter 并设置超时/错误处理器
 * 2. 创建/获取对话 ID
 * 3. 构建 ConversationContext 并标记为流式模式
 * 4. 通过 pipelineExecutor.executeStreaming() 运行预处理 Stage
 * 5. ModelRoutingStage 发起异步 LLM 调用，完成后触发 post-processing
 * 6. 立即返回 SseEmitter 给客户端
 *
 * Agent 模式（LangChain4j 1.4.0+）：
 * 异步执行 executeWithAgentLoop，立即返回 emitter 让 Spring MVC 建立 SSE 连接。
 * ModelRoutingStage 内部使用 StreamingChatModel，onPartialResponse 推送的 token
 * 在连接建立后实时到达客户端，实现真正的 token 级流式输出。
 * Agent 循环 + 后处理完成后由 StreamingDoneStage 发送 done 事件。
 */
@Service
@Slf4j
public class StreamingService {

    private final ChatWorkflowService chatWorkflowService;
    private final ContextPipelineExecutor pipelineExecutor;
    private final PipelineEntryDispatcher pipelineEntryDispatcher;
    private final StreamingProperties streamingProperties;
    private final ThreadPoolTaskExecutor streamingTaskExecutor;

    public StreamingService(ChatWorkflowService chatWorkflowService,
            ContextPipelineExecutor pipelineExecutor,
            PipelineEntryDispatcher pipelineEntryDispatcher,
            StreamingProperties streamingProperties,
            @Qualifier("streamingTaskExecutor") ThreadPoolTaskExecutor streamingTaskExecutor) {
        this.chatWorkflowService = chatWorkflowService;
        this.pipelineExecutor = pipelineExecutor;
        this.pipelineEntryDispatcher = pipelineEntryDispatcher;
        this.streamingProperties = streamingProperties;
        this.streamingTaskExecutor = streamingTaskExecutor;
    }

    /**
     * 处理流式聊天请求。
     *
     * @param request 聊天请求
     * @return SSE Emitter，用于流式推送响应
     */
    public SseEmitter streamResponse(ChatRequest request) {
        SseEmitter emitter = new SseEmitter(streamingProperties.getSseTimeoutMs());

        // 延迟 ctx 创建，让回调能拿到 ctx 引用设置 clientCancelled
        final ConversationContext ctxHolder[] = new ConversationContext[1];

        emitter.onTimeout(() -> {
            log.warn("[STREAM] SSE emitter timeout (after {}ms)", streamingProperties.getSseTimeoutMs());
            if (ctxHolder[0] != null) {
                ctxHolder[0].markClientCancelled();
                log.info("[STREAM] clientCancelled flag set on timeout, LLM callback will short-circuit");
            }
            emitter.complete();
        });
        emitter.onError(e -> {
            log.error("[STREAM] SSE emitter error: {}", e.getMessage());
            if (ctxHolder[0] != null) {
                ctxHolder[0].markClientCancelled();
            }
        });

        // 聊天 SSE 与通知 SSE 完全隔离：
        // 通知推送由 NotificationSseManager 独立管理（/api/notifications/stream），
        // 聊天 emitter 仅负责流式响应，不再注册到通知服务。

        String conversationId = chatWorkflowService.getOrCreateConversationId(request);

        ConversationContext ctx = ConversationContext.fromRequest(request);
        ctx.setConversationId(conversationId);
        ctx.setStreaming(true);
        ctx.setAgentMode(request.isAgentMode());
        ctx.setSseEmitter(emitter);
        ctxHolder[0] = ctx;
        // 注入后处理钩子：非 Agent 流式路径由 ModelRoutingStage 在回调中触发，
        // 避免 Stage 反向依赖 ContextPipelineExecutor 造成循环依赖。
        ctx.setPostStreamingHook(() -> pipelineExecutor.executePostProcessing(ctx));

        if (ctx.isAgentMode()) {
            // 入口调度统一委托给 PipelineEntryDispatcher（同 ChatService）
            // 异步执行循环，立即返回 emitter 让 Spring MVC 建立 SSE 连接。
            // 否则循环同步阻塞请求线程，期间 emitter.send 的 token
            // 会被 Spring 的 earlyEvents 缓存，等连接建立后一次性回放，前端看到的是
            // "全部一起到"而非流式。
            // 使用专用 streamingTaskExecutor（不走 ForkJoinPool.commonPool），
            // 避免 LLM 长任务耗尽 commonPool 影响其他业务。
            CompletableFuture.runAsync(() -> {
                try {
                    pipelineEntryDispatcher.executeAgentChat(ctx);
                } catch (Exception e) {
                    log.error("[STREAM] Agent chat failed: {}", e.getMessage(), e);
                    // 客户端已断连时不发 error SSE（连接已关，发了也送不到）
                    if (!ctx.isClientCancelled()) {
                        ctx.emitSseEvent("error",
                                "{\"message\": \"" + JsonUtils.escapeJson(e.getMessage()) + "\"}");
                    }
                    emitter.completeWithError(e);
                }
            }, streamingTaskExecutor);
        } else {
            ctx.setPipelineType(ConversationContext.PipelineType.STREAMING_CHAT);
            pipelineExecutor.executeStreaming(ctx);
        }

        return emitter;
    }
}
