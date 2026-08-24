package com.example.app.pipeline.context;

import com.example.app.entity.ModelConfig;
import lombok.Builder;
import lombok.Data;

/**
 * LLM 执行状态，包括模型配置、响应、流式标记和持久化跟踪。
 *
 * <p>并发可见性：{@link #llmResponse}、{@link #generatedTitle}、{@link #clientCancelled}
 * 可能由 SSE 容器线程（onTimeout/onError）、流式回调线程、异步执行线程交叉读写，
 * 因此标注 {@code volatile} 保证跨线程可见性。
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

    /**
     * 客户端已断连标志。
     *
     * <p>由 SSE 容器在 emitter.onTimeout / onError 回调里设置（SSE 容器线程），
     * 由以下位置读取：
     * <ul>
     *   <li>ModelRoutingStage 的 onPartialResponse / streamReplayChunks
     *       —— 检测后停止缓冲/推送，直接 countDown 释放 latch，不再等满 10min</li>
     *   <li>ContextPipelineExecutor.runReActLoop 循环条件
     *       —— 检测后提前 break，不再跑下一轮 LLM 调用，节省 token</li>
     *   <li>StreamingService 异步 catch —— 检测后跳过 error SSE 推送</li>
     * </ul>
     *
     * <p>设计目标：客户端断开后尽快短路 LLM 调用与循环，避免继续生成无用 token
     * 浪费上游 API 费用。注意：HTTP 请求一旦发出无法真正"取消"上游模型生成，
     * 但能避免后续轮次的 LLM 调用和工具执行。
     */
    private volatile boolean clientCancelled;
}