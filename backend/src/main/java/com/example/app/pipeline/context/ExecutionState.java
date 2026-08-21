package com.example.app.pipeline.context;

import com.example.app.entity.ModelConfig;
import lombok.Builder;
import lombok.Data;

/**
 * LLM 执行状态，包括模型配置、响应、流式标记和持久化跟踪。
 *
 * <p>并发可见性：{@link #llmResponse} 和 {@link #generatedTitle} 可能由流式回调线程
 * 写入、由主线程读取，因此标注 {@code volatile} 保证跨线程可见性。
 */
@Data
@Builder(toBuilder = true)
public class ExecutionState {
    private ModelConfig customModelConfig;
    private volatile String llmResponse;
    private boolean streaming;
    private Object sseEmitter;
    private volatile String generatedTitle;
    private boolean userMessagePersisted;
    private boolean userMessageInMemory;

    /**
     * 流式完成后的后处理回调钩子。
     *
     * <p>由调用方（{@code StreamingService}）在执行 pipeline 前注入，
     * 指向 {@code pipelineExecutor.executePostProcessing(ctx)}。
     * ModelRoutingStage 在非 Agent 流式回调中调用 {@link Runnable#run()} 触发后处理，
     * 避免 Stage 反向依赖 Executor 造成循环依赖。
     */
    private Runnable postStreamingHook;
}