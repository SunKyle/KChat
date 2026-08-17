package com.example.app.pipeline.context;

import com.example.app.entity.ModelConfig;
import lombok.Builder;
import lombok.Data;

/**
 * LLM 执行状态，包括模型配置、响应、流式标记和持久化跟踪。
 */
@Data
@Builder(toBuilder = true)
public class ExecutionState {
    private ModelConfig customModelConfig;
    private String llmResponse;
    private boolean streaming;
    private Object sseEmitter;
    private String generatedTitle;
    private boolean userMessagePersisted;
    private boolean userMessageInMemory;
}