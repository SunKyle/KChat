package com.example.app.service;

import com.example.app.dto.ChatRequest;
import com.example.app.pipeline.ContextPipelineExecutor;
import com.example.app.pipeline.context.ConversationContext;
import com.example.app.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.CompletableFuture;

/**
 * 流式响应服务，处理 SSE 流式消息传输。
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
@RequiredArgsConstructor
@Slf4j
public class StreamingService {

    private final ChatWorkflowService chatWorkflowService;
    private final ContextPipelineExecutor pipelineExecutor;

    /**
     * 处理流式聊天请求。
     *
     * @param request 聊天请求
     * @return SSE Emitter，用于流式推送响应
     */
    public SseEmitter streamResponse(ChatRequest request) {
        SseEmitter emitter = new SseEmitter(300000L);

        emitter.onTimeout(() -> {
            log.warn("[STREAM] SSE emitter timeout");
            emitter.complete();
        });
        emitter.onError(e -> log.error("[STREAM] SSE emitter error: {}", e.getMessage()));

        // 聊天 SSE 与通知 SSE 完全隔离：
        // 通知推送由 NotificationSseManager 独立管理（/api/notifications/stream），
        // 聊天 emitter 仅负责流式响应，不再注册到通知服务。

        String conversationId = chatWorkflowService.getOrCreateConversationId(request);

        ConversationContext ctx = ConversationContext.fromRequest(request);
        ctx.setConversationId(conversationId);
        ctx.setStreaming(true);
        ctx.setAgentMode(request.isAgentMode());
        ctx.setSseEmitter(emitter);

        if (ctx.isAgentMode()) {
            ctx.setPipelineType(ConversationContext.PipelineType.AGENT_CHAT);
            // Agent 模式：异步执行循环，立即返回 emitter 让 Spring MVC 建立 SSE 连接。
            // 否则 executeWithAgentLoop 同步阻塞请求线程，期间 emitter.send 的 token
            // 会被 Spring 的 earlyEvents 缓存，等连接建立后一次性回放，前端看到的是
            // "全部一起到"而非流式。
            CompletableFuture.runAsync(() -> {
                try {
                    pipelineExecutor.executeWithAgentLoop(ctx);
                    pipelineExecutor.executePostProcessing(ctx);
                } catch (Exception e) {
                    log.error("[STREAM] Agent loop failed: {}", e.getMessage(), e);
                    ctx.emitSseEvent("error", "{\"message\": \"" + JsonUtils.escapeJson(e.getMessage()) + "\"}");
                    emitter.completeWithError(e);
                }
            });
        } else {
            ctx.setPipelineType(ConversationContext.PipelineType.STREAMING_CHAT);
            pipelineExecutor.executeStreaming(ctx);
        }

        return emitter;
    }
}
