package com.example.app.pipeline.stage.agent;

import com.example.app.dto.Artifact;
import com.example.app.pipeline.ContextPipelineStage;
import com.example.app.pipeline.context.ConversationContext;
import com.example.app.pipeline.stage.agent.tool.SkillToolSpecFactory;
import com.example.app.service.tool.SkillExecutor;
import com.example.app.service.tool.ToolExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Agent 工具调用执行阶段
 *
 * <p>遍历 ctx.toolCalls，根据工具名前缀分两条路径：
 * <ol>
 *   <li><b>ORCHESTRATOR 层伪 Skill 调用</b>（{@code toolName.startsWith("call_skill_")}）
 *       → 分发给 {@link SkillExecutor}，内部会 push Specialist 帧 + 跑内层
 *       Agent Loop + pop，返回 ToolResultRecord 作为 Orchestrator 这一轮的 Observation。</li>
 *   <li><b>SPECIALIST 层原子 Tool 调用</b>（其他 toolName）
 *       → 走原有 {@link ToolExecutor}，反射调用真实 ToolComponent Bean。</li>
 * </ol>
 *
 * <p>单个工具执行失败不会中断整体流程：失败结果以 success=false 标记，
 * 后续 LLM 轮次可据此决定是否重试或换一种方式回答。
 *
 * <p>order=650（AGENT 阶段，在 toolCallDetectionStage(610) 之后）
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ToolInvocationStage implements ContextPipelineStage {

    private final ToolExecutor toolExecutor;
    private final SkillExecutor skillExecutor;

    /**
     * 匹配 Markdown 图片语法：![alt](url)
     * URL 支持 http(s):// 或 data:image/...;base64,... 两种形式。
     */
    private static final Pattern IMAGE_MARKDOWN_PATTERN = Pattern.compile(
            "!\\[[^\\]]*\\]\\((https?://[^\\s)]+|data:image/[^\\s)]+)\\)");

    @Override
    public Phase getPhase() {
        return Phase.AGENT;
    }

    @Override
    public String getName() {
        return "toolInvocationStage";
    }

    @Override
    public void execute(ConversationContext ctx) {
        if (ctx.getToolCalls().isEmpty()) {
            return;
        }

        for (ConversationContext.ToolCallRecord call : ctx.getToolCalls()) {
            long t0 = System.currentTimeMillis();
            ConversationContext.ToolResultRecord result;

            boolean isSkillCall = call.toolName() != null
                    && call.toolName().startsWith(SkillToolSpecFactory.skillCallPrefix());

            if (isSkillCall) {
                result = skillExecutor.execute(call, ctx);
            } else {
                result = toolExecutor.execute(call, ctx.getUserId());
            }
            long duration = System.currentTimeMillis() - t0;
            ctx.getToolResults().add(result);

            String resultSummary = result.success()
                    ? truncate(String.valueOf(result.result()), 200)
                    : result.errorMessage();
            ctx.getTrace().addToolCall(
                    ctx.getCurrentIteration(),
                    call.toolName(),
                    truncate(call.arguments(), 300),
                    result.success(),
                    resultSummary,
                    duration,
                    result.success() ? null : result.errorMessage());

            if (!result.success()) {
                log.warn("[ToolInvocation] {} '{}' failed ({}ms): {}",
                        isSkillCall ? "SKILL" : "TOOL",
                        call.toolName(), duration, result.errorMessage());
            } else {
                log.info("[ToolInvocation] {} '{}' succeeded ({}ms): {}",
                        isSkillCall ? "SKILL" : "TOOL",
                        call.toolName(), duration, resultSummary);
                // 扫描 Tool 返回结果中的图片 Markdown，自动提取 URL 加入 ctx.artifacts
                collectImageArtifacts(ctx, call.toolName(), String.valueOf(result.result()));
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("toolName", call.toolName());
            data.put("toolCallId", call.toolCallId());
            data.put("category", isSkillCall ? "SKILL" : "ATOMIC_TOOL");
            data.put("success", result.success());
            data.put("result", result.result());
            data.put("model", result.model());
            data.put("durationMs", duration);
            if (!result.success()) {
                data.put("errorMessage", result.errorMessage());
            }
            ctx.emitAgentThinking("tool_execution", data);
        }

        log.info("[ToolInvocation] Executed {} call(s), {} success, {} failed",
                ctx.getToolCalls().size(),
                ctx.getToolResults().stream().filter(ConversationContext.ToolResultRecord::success).count(),
                ctx.getToolResults().stream().filter(r -> !r.success()).count());
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }

    private void collectImageArtifacts(ConversationContext ctx, String toolName, String resultText) {
        if (resultText == null || resultText.isBlank()) return;
        Matcher matcher = IMAGE_MARKDOWN_PATTERN.matcher(resultText);
        boolean found = false;
        while (matcher.find()) {
            String url = matcher.group(1);
            if (ctx.getArtifacts() == null) ctx.setArtifacts(new ArrayList<>());
            ctx.getArtifacts().add(Artifact.image(url, "Generated by " + toolName));
            found = true;
            log.info("[ToolInvocation] {} produced image artifact: {}", toolName,
                    url.length() > 80 ? url.substring(0, 80) + "..." : url);
        }
        if (!found) return;
        List<Artifact> artifacts = ctx.getArtifacts();
        if (artifacts.size() > 1) {
            List<Artifact> deduped = new ArrayList<>();
            for (Artifact a : artifacts) {
                boolean dup = false;
                for (Artifact existing : deduped) {
                    if (a.url() != null && a.url().equals(existing.url()) && "image".equals(a.type())) {
                        dup = true; break;
                    }
                }
                if (!dup) deduped.add(a);
            }
            ctx.setArtifacts(deduped);
        }
    }

    @Override
    public boolean isApplicable(ConversationContext ctx) {
        return ctx.isAgentMode();
    }

    @Override
    public int getOrder() {
        return 650;
    }

    @Override
    public boolean isCritical() {
        return false;
    }
}
