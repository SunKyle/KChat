package com.example.app.pipeline.context;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

/**
 * Structured trace of a single pipeline execution.
 *
 * <p>Records per-stage timing/status, agent iteration decisions, and tool call
 * lifecycle. Emitted as a structured JSON log by {@code PipelineAuditStage}
 * for easy debugging and analysis.
 *
 * <p>Thread-safe for the lifetime of a single request (not shared across requests).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PipelineTrace {

    private long startTime;
    private long endTime;
    private final List<StageEntry> stages = new ArrayList<>();
    private final List<AgentIterationEntry> agentIterations = new ArrayList<>();
    private final List<ToolCallEntry> toolCalls = new ArrayList<>();

    // ── Stage entries ─────────────────────────────────────────────

    public void addStage(StageEntry entry) {
        stages.add(entry);
    }

    public void addStage(String name, int order, String phase,
                         long durationMs, String status, String errorMessage) {
        stages.add(new StageEntry(name, order, phase, durationMs, status, errorMessage));
    }

    // ── Agent iteration entries ───────────────────────────────────

    public void addAgentIteration(int iteration, long durationMs,
                                  String llmSummary, String decision,
                                  int toolCallCount) {
        agentIterations.add(new AgentIterationEntry(
                iteration, durationMs, llmSummary, decision, toolCallCount));
    }

    // ── Tool call entries ─────────────────────────────────────────

    public void addToolCall(int iteration, String toolName, String arguments,
                            boolean success, String resultSummary,
                            long durationMs, String errorMessage) {
        toolCalls.add(new ToolCallEntry(iteration, toolName, arguments,
                success, resultSummary, durationMs, errorMessage));
    }

    // ── Summary helpers ───────────────────────────────────────────

    public long getTotalDurationMs() {
        return endTime - startTime;
    }

    public int getSuccessCount() {
        return (int) stages.stream().filter(s -> "SUCCESS".equals(s.status())).count();
    }

    public int getFailedCount() {
        return (int) stages.stream().filter(s -> "FAILED".equals(s.status())).count();
    }

    public long getPhaseDuration(String phase) {
        return stages.stream()
                .filter(s -> phase.equals(s.phase()))
                .mapToLong(StageEntry::durationMs)
                .sum();
    }

    // ── Nested records ────────────────────────────────────────────

    public record StageEntry(
            String name,
            int order,
            String phase,
            long durationMs,
            String status,      // SUCCESS | FAILED | SKIPPED
            String errorMessage
    ) {}

    public record AgentIterationEntry(
            int iteration,
            long durationMs,
            String llmSummary,  // "text response" or "tool_call: searchTodos"
            String decision,    // CONTINUE | TERMINATE
            int toolCallCount
    ) {}

    public record ToolCallEntry(
            int iteration,
            String toolName,
            String arguments,
            boolean success,
            String resultSummary,
            long durationMs,
            String errorMessage
    ) {}

    // ── Getters ───────────────────────────────────────────────────

    public long getStartTime() { return startTime; }
    public void setStartTime(long startTime) { this.startTime = startTime; }
    public long getEndTime() { return endTime; }
    public void setEndTime(long endTime) { this.endTime = endTime; }
    public List<StageEntry> getStages() { return stages; }
    public List<AgentIterationEntry> getAgentIterations() { return agentIterations; }
    public List<ToolCallEntry> getToolCalls() { return toolCalls; }
}
