package com.example.app.service;

import com.example.app.dto.ChatRequest;
import com.example.app.pipeline.ContextPipelineExecutor;
import com.example.app.pipeline.context.ConversationContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

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

        String conversationId = chatWorkflowService.getOrCreateConversationId(request);

        ConversationContext ctx = ConversationContext.fromRequest(request);
        ctx.setConversationId(conversationId);
        ctx.setStreaming(true);
        ctx.setSseEmitter(emitter);
        ctx.setPipelineType(ConversationContext.PipelineType.STREAMING_CHAT);

        pipelineExecutor.executeStreaming(ctx);

        return emitter;
    }
}
