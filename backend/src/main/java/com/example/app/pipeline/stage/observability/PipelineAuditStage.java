package com.example.app.pipeline.stage.observability;

import com.example.app.pipeline.ContextPipelineStage;
import com.example.app.pipeline.context.ConversationContext;
import com.example.app.pipeline.context.PipelineTrace;
import com.example.app.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Pipeline 审计日志 — 在 Pipeline 执行结束时输出结构化 trace。
 *
 * <p>输出内容包括：
 * <ul>
 *   <li>各 Stage 的执行顺序、耗时、状态（SUCCESS/FAILED）</li>
 *   <li>Agent 循环的迭代记录（决策、工具调用次数）</li>
 *   <li>工具调用的完整生命周期（名称、参数、结果、耗时）</li>
 *   <li>按 Phase 汇总耗时</li>
 * </ul>
 *
 * <p>日志级别：INFO（便于日常排查），格式为可读的结构化文本 + JSON。
 *
 * <p>order=999（OBSERVABILITY 阶段，最后执行）
 */
@Component
@Slf4j
public class PipelineAuditStage implements ContextPipelineStage {

    @Override
    public Phase getPhase() { return Phase.OBSERVABILITY; }

    public String getName() {
        return "pipelineAuditStage";
    }

    @Override
    public void execute(ConversationContext ctx) {
        PipelineTrace trace = ctx.getTrace();
        trace.setEndTime(System.currentTimeMillis());

        long totalMs = trace.getTotalDurationMs();

        // ── 构建可读的结构化日志 ──
        StringBuilder sb = new StringBuilder();

        // Header
        sb.append("\n");
        sb.append("╔══════════════════════════════════════════════════════════════╗\n");
        sb.append("║  PIPELINE TRACE                                              ║\n");
        String convId = ctx.getConversationId();
        if (convId != null && convId.length() > 12) {
            convId = convId.substring(0, 12) + "...";
        }
        sb.append(String.format("║  对话: %-52s║%n", convId));
        sb.append(String.format("║  总耗时: %5dms  |  Stages: %d (✅%d ❌%d)  |  Agent迭代: %d  |  工具调用: %d║%n",
                totalMs,
                trace.getStages().size(),
                trace.getSuccessCount(),
                trace.getFailedCount(),
                trace.getAgentIterations().size(),
                trace.getToolCalls().size()));
        sb.append("╠══════════════════════════════════════════════════════════════╣\n");

        // Stage entries grouped by phase
        Map<String, List<PipelineTrace.StageEntry>> byPhase = trace.getStages().stream()
                .collect(Collectors.groupingBy(PipelineTrace.StageEntry::phase,
                        LinkedHashMap::new, Collectors.toList()));

        for (Map.Entry<String, List<PipelineTrace.StageEntry>> phaseEntry : byPhase.entrySet()) {
            String phase = phaseEntry.getKey();
            long phaseTotal = phaseEntry.getValue().stream()
                    .mapToLong(PipelineTrace.StageEntry::durationMs).sum();
            sb.append(String.format("║  ── %s (%dms) ──%-44s║%n",
                    phase, phaseTotal, ""));

            for (PipelineTrace.StageEntry stage : phaseEntry.getValue()) {
                String icon = "FAILED".equals(stage.status()) ? "❌" : "✅";
                String name = stage.name();
                if (name.length() > 28) {
                    name = name.substring(0, 25) + "...";
                }
                String error = stage.errorMessage() != null
                        ? " ⚠ " + truncate(stage.errorMessage(), 30) : "";
                sb.append(String.format("║    %s %3d %-28s %5dms%s%-8s║%n",
                        icon, stage.order(), name, stage.durationMs(), error,
                        ""));
            }
        }

        // Agent iterations
        if (!trace.getAgentIterations().isEmpty()) {
            sb.append("╠══════════════════════════════════════════════════════════════╣\n");
            sb.append("║  ── AGENT DECISION CHAIN ──%-41s║%n".formatted(""));

            for (PipelineTrace.AgentIterationEntry iter : trace.getAgentIterations()) {
                String summary = iter.llmSummary() != null ? iter.llmSummary() : "—";
                sb.append(String.format("║    [%d] %-12s tools=%-2d  %s%-20s║%n",
                        iter.iteration(),
                        iter.decision(),
                        iter.toolCallCount(),
                        truncate(summary, 30),
                        ""));
            }
        }

        // Tool calls
        if (!trace.getToolCalls().isEmpty()) {
            sb.append("╠══════════════════════════════════════════════════════════════╣\n");
            sb.append("║  ── TOOL CALLS ──%-49s║%n".formatted(""));

            for (PipelineTrace.ToolCallEntry tc : trace.getToolCalls()) {
                String icon = tc.success() ? "✅" : "❌";
                String result = tc.success()
                        ? truncate(tc.resultSummary(), 40)
                        : truncate(tc.errorMessage(), 40);
                sb.append(String.format("║    %s [%d] %-20s %4dms  %s%-8s║%n",
                        icon, tc.iteration(), tc.toolName(),
                        tc.durationMs(), result, ""));
                if (tc.arguments() != null && !tc.arguments().equals("{}")) {
                    sb.append(String.format("║         args: %s%-48s║%n",
                            truncate(tc.arguments(), 48)));
                }
            }
        }

        sb.append("╚══════════════════════════════════════════════════════════════╝");

        log.info(sb.toString());

        // Also emit as structured JSON for programmatic analysis
        Map<String, Object> jsonTrace = new LinkedHashMap<>();
        jsonTrace.put("conversationId", ctx.getConversationId());
        jsonTrace.put("totalDurationMs", totalMs);
        jsonTrace.put("stageCount", trace.getStages().size());
        jsonTrace.put("successCount", trace.getSuccessCount());
        jsonTrace.put("failedCount", trace.getFailedCount());
        jsonTrace.put("stages", trace.getStages());
        jsonTrace.put("agentIterations", trace.getAgentIterations());
        jsonTrace.put("toolCalls", trace.getToolCalls());

        log.info("[PipelineTrace] JSON: {}", JsonUtils.toJson(jsonTrace));
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }

    @Override
    public int getOrder() {
        return 999;
    }

    @Override
    public boolean isCritical() {
        return false;
    }
}
