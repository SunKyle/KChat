package com.example.app.pipeline.context;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 流水线编排与观测状态。
 */
@Data
@Builder(toBuilder = true)
public class PipelineOrchestration {
    private PipelineType pipelineType;
    private int maxAgentIterations;
    private int currentIteration;
    private long pipelineStartTime;

    @Builder.Default
    private List<String> executedStageNames = new ArrayList<>();
    @Builder.Default
    private Map<String, Long> stageTimings = new LinkedHashMap<>();
    @Builder.Default
    private List<ConversationContext.PipelineError> errors = new ArrayList<>();
    @Builder.Default
    private PipelineTrace trace = new PipelineTrace();

    public void recordStage(String name, long durationMs) {
        executedStageNames.add(name);
        stageTimings.put(name, durationMs);
    }

    public void addError(String stageName, String message, Throwable cause, boolean recoverable) {
        errors.add(new ConversationContext.PipelineError(stageName, message, cause, recoverable));
    }

    public boolean hasCriticalErrors() {
        return errors.stream().anyMatch(e -> !e.recoverable());
    }

    public enum PipelineType {
        SIMPLE_CHAT,
        STREAMING_CHAT,
        AGENT_CHAT
    }
}
