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
     *
     * Agent 模式（LangChain4j 1.4.0+）：
     * 走 executeWithAgentLoop 同步执行，但 ModelRoutingStage 内部使用
     * StreamingChatModel 实现真正的 token 级流式输出。
     * Agent 循环完成后仅发送 done 事件（token 已在循环内通过 partial 推送）。
     */
    @Service
    @RequiredArgsConstructor
    @Slf4j
    public class StreamingService {

    private final ChatWorkflowService chatWorkflowService;
    private final ContextPipelineExecutor pipelineExecutor;
    private final NotificationService notificationService;

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

        // 注册 emitter 到通知服务，支持后台提醒推送
        if (request.getUserId() != null) {
            notificationService.registerEmitter(request.getUserId(), emitter);
        }

        String conversationId = chatWorkflowService.getOrCreateConversationId(request);

        ConversationContext ctx = ConversationContext.fromRequest(request);
        ctx.setConversationId(conversationId);
        ctx.setStreaming(true);
        ctx.setAgentMode(request.isAgentMode());
        ctx.setSseEmitter(emitter);

        if (ctx.isAgentMode()) {
            ctx.setPipelineType(ConversationContext.PipelineType.AGENT_CHAT);
            // Agent 模式：同步执行循环，但 LLM 调用内部已实现真正流式
            pipelineExecutor.executeWithAgentLoop(ctx);
            // 执行后处理（含 streamingDoneStage 发送 done 事件）
            pipelineExecutor.executePostProcessing(ctx);
        } else {
            ctx.setPipelineType(ConversationContext.PipelineType.STREAMING_CHAT);
            pipelineExecutor.executeStreaming(ctx);
        }

        return emitter;
    }
}
