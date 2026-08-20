package com.example.app.service.tool;

import com.example.app.dto.Artifact;
import com.example.app.dto.CreateNoteRequest;
import com.example.app.entity.Skill;
import com.example.app.entity.SkillUsage;
import com.example.app.pipeline.ContextPipelineExecutor;
import com.example.app.pipeline.context.AgentFrame;
import com.example.app.pipeline.context.AgentStack;
import com.example.app.pipeline.context.ConversationContext;
import com.example.app.repository.SkillUsageRepository;
import com.example.app.service.NoteService;
import com.example.app.service.SkillService;
import com.example.app.util.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SkillExecutor —— Orchestrator 层调用 Skill 的执行器。
 *
 * <p>职责（单次调用的完整生命周期）：
 * <ol>
 *   <li>根据 toolName（call_skill_&lt;skillId&gt;）查找 Skill 实体</li>
 *   <li>push 一个 SPECIALIST 栈帧到 AgentStack，携带 inputArgs</li>
 *   <li>在新帧内：写入 KEY_ACTIVE_SKILL → 组装初始 UserMessage（从 instruction 入参合成）
 *       → 调用 {@link ContextPipelineExecutor#executeWithAgentLoop(ConversationContext)}
 *       走内层 Specialist ReAct 循环（LLM 调原子 Tool）</li>
 *   <li>循环结束后：标记帧 FINISHED，把 llmResponse / outputPayload 暂存</li>
 *   <li>pop 栈帧，thinkingSteps 合并到父帧</li>
 *   <li>构造 ToolResultRecord，结果为 Skill 的最终文本（success=true），
 *       回填到 Orchestrator 帧的 toolResults，作为下一轮 Orchestrator LLM 的 Observation</li>
 * </ol>
 *
 * <p>错误策略：任何一步失败都返回 success=false 的 ToolResultRecord，
 * Orchestrator LLM 下一轮可据此决定换 Skill 或走通用回答。
 *
 * <p>注意：本类由 ToolInvocationStage 分发调用，调用时当前帧是 Orchestrator，
 * 本方法内部 push/pop，返回时栈深度恢复为 1。
 */
@Component
@Slf4j
public class SkillExecutor {

    private final SkillService skillService;
    private final ContextPipelineExecutor pipelineExecutor;
    private final NoteService noteService;
    private final SkillUsageRepository skillUsageRepository;

    // 手写构造器：pipelineExecutor 需 @Lazy 打断与 ToolInvocationStage 的循环依赖
    // (ContextPipelineExecutor → ToolInvocationStage → SkillExecutor → ContextPipelineExecutor)
    public SkillExecutor(
            SkillService skillService,
            @Lazy ContextPipelineExecutor pipelineExecutor,
            NoteService noteService,
            SkillUsageRepository skillUsageRepository) {
        this.skillService = skillService;
        this.pipelineExecutor = pipelineExecutor;
        this.noteService = noteService;
        this.skillUsageRepository = skillUsageRepository;
    }

    private static final TypeReference<Map<String, Object>> MAP_OBJECT = new TypeReference<>() {};

    /**
     * 执行一次 Skill 调用（由 ToolInvocationStage 分发 call_skill_* 时调用）。
     *
     * @param call   ToolCallRecord，toolName 以 call_skill_ 开头
     * @param ctx    上下文（当前帧应为 Orchestrator）
     * @return ToolResultRecord，回填给 Orchestrator 帧 toolResults
     */
    public ConversationContext.ToolResultRecord execute(
            ConversationContext.ToolCallRecord call,
            ConversationContext ctx) {

        String toolName = call.toolName();
        String toolCallId = call.toolCallId();
        String skillId = com.example.app.pipeline.stage.agent.tool.SkillToolSpecFactory.extractSkillId(toolName);

        if (skillId == null) {
            return fail(call, "Invalid skill call prefix: " + toolName);
        }

        Skill skill = skillService.getEntityById(skillId);
        if (skill == null) {
            return fail(call, "Skill not found: " + skillId);
        }

        // 1. 解析 inputArgs
        Map<String, Object> inputArgs = parseArguments(call.arguments());
        AgentStack stack = ctx.getAgentStack();
        int orchFrameId = stack.currentFrameId();

        long t0 = System.currentTimeMillis();
        Object resultPayload;
        boolean success;
        String errorMsg = null;

        int iterations = 0;
        String skillLlmResponse = null;
        SkillUsage.UsageStatus usageStatus = SkillUsage.UsageStatus.RUNNING;
        String hookErrorMsg = null;

        try {
            // 2. push SPECIALIST 帧
            int maxIt = skill.getMaxIterations() != null ? skill.getMaxIterations() : 5;
            stack.pushSkillFrame(skillId, inputArgs, null, maxIt);
            AgentFrame specialist = stack.peek();

            // 推送 Agent 思考：Orchestrator → Skill 分派事件
            // 注意：必须在 pushSkillFrame 之后 emit，让 skill_enter 携带 SPECIALIST 帧的 frameId，
            // 这样前端的 skill_enter / 内部 llm_call / skill_exit 三者 frameId 一致，才能正确分组。
            Map<String, Object> enterData = new LinkedHashMap<>();
            enterData.put("skillId", skill.getId());
            enterData.put("skillName", skill.getName());
            enterData.put("inputArgs", inputArgs);
            enterData.put("maxIterations", skill.getMaxIterations());
            ctx.emitAgentThinking("skill_enter", enterData);

            // 3. 在 Specialist 帧内写入 KEY_ACTIVE_SKILL + 组装初始消息
            specialist.getAgentTool().getAgentState().put(ConversationContext.KEY_ACTIVE_SKILL, skill);
            ctx.setActiveSkillId(skillId); // Facade → peek().skillId + agentTool.activeSkillId

            // 把 instruction 转为这一帧的"用户消息"
            String instructionText = buildInstructionForSpecialist(inputArgs, skill);
            UserMessage specialistUserMsg = UserMessage.from(instructionText);
            List<ChatMessage> specialistMessages = new ArrayList<>();
            // 短期记忆：目前 MVP 不把 Orchestrator 的对话历史灌进 Specialist（避免上下文爆炸），
            // 未来可按需叠加 ctx.getShortTermMemory() 的尾部若干条。
            specialistMessages.add(specialistUserMsg);
            ctx.setAssembledMessages(specialistMessages);

            // 4. 执行内层 ReAct 循环（只跑 ASSEMBLY+EXECUTION+AGENT，不跑 PREPROCESS）
            // PREPROCESS 已在顶层 executeWithOrchestratorLoop 跑过，
            // SPECIALIST 帧不需要重复预持久化 user message 等预处理。
            pipelineExecutor.executeSpecialistLoop(ctx);

            // 5. 取结果
            specialist.setStatus(AgentFrame.FrameStatus.FINISHED);
            resultPayload = specialist.getOutputPayload();
            if (resultPayload == null) {
                // 没写 outputPayload 的话，兜底用 llmResponse 文本作为结果回传 Orchestrator
                resultPayload = ctx.getLlmResponse();
            }
            skillLlmResponse = ctx.getLlmResponse();
            iterations = ctx.getCurrentIteration();
            success = true;
            usageStatus = SkillUsage.UsageStatus.COMPLETED;
            log.info("[SkillExecutor] Skill '{}' completed in {}ms, iterations={}, result length={}",
                    skill.getName(), System.currentTimeMillis() - t0, iterations,
                    resultPayload != null ? String.valueOf(resultPayload).length() : 0);

            // 5b. 执行 Skill 完成钩子（副作用）——仅成功路径
            try {
                runCompletionHook(ctx, skill, skillLlmResponse);
            } catch (Exception he) {
                log.warn("[SkillExecutor] Completion hook failed for '{}': {}", skill.getName(), he.getMessage());
                usageStatus = SkillUsage.UsageStatus.PARTIAL;
                hookErrorMsg = he.getMessage();
            }

        } catch (Exception e) {
            log.error("[SkillExecutor] Skill '{}' failed: {}", skill.getName(), e.getMessage(), e);
            success = false;
            errorMsg = e.getMessage();
            resultPayload = null;
            usageStatus = SkillUsage.UsageStatus.FAILED;
            // 标记帧 FAILED（如果还在栈上）
            if (!stack.isAtOrchestrator()) {
                stack.peek().setStatus(AgentFrame.FrameStatus.FAILED);
                stack.peek().setErrorMessage(errorMsg);
            }
        }

        // 5c. 写 SkillUsage（无论成功失败都写）——在 pop 帧前完成（避免丢失 specialist 数据）
        try {
            saveSkillUsage(ctx, skill, inputArgs, skillLlmResponse, usageStatus,
                    errorMsg != null ? errorMsg : hookErrorMsg, iterations);
        } catch (Exception ue) {
            log.warn("[SkillExecutor] Failed to save SkillUsage for '{}': {}", skill.getName(), ue.getMessage());
        }

        // 6. 推送 Agent 思考：Skill 退出事件
        // 注意：必须在 popFrame 之前 emit，让 skill_exit 携带 SPECIALIST 帧的 frameId，
        // 与 skill_enter 及内部步骤保持一致。
        Map<String, Object> exitData = new LinkedHashMap<>();
        exitData.put("skillId", skill.getId());
        exitData.put("skillName", skill.getName());
        exitData.put("success", success);
        exitData.put("durationMs", System.currentTimeMillis() - t0);
        if (!success) exitData.put("errorMessage", errorMsg);
        ctx.emitAgentThinking("skill_exit", exitData);

        // 7. 如果 Specialist 帧还在栈上，pop（异常路径也确保 pop，避免栈泄漏）
        if (!stack.isAtOrchestrator()) {
            stack.popFrame();
        }

        // 7. 构造 ToolResultRecord（注意：是给 Orchestrator 层的 Observation）
        String resultText = resultPayload != null ? String.valueOf(resultPayload) : "";
        // Specialist 期间产生的 Artifacts 已经写入顶层 ctx.artifacts（共享），无需额外搬运。
        return new ConversationContext.ToolResultRecord(
                toolName,
                toolCallId,
                resultText,
                success,
                errorMsg,
                null  // Skill 级别的调用不展示"用了哪个模型"，模型在子 tool 粒度展示
        );
    }

    // ── 辅助方法 ──────────────────────────────────────────────

    /**
     * 把 Orchestrator LLM 传入的 arguments JSON 解析为 Map。
     * 解析失败返回空 Map，避免单点故障中断链路。
     */
    private Map<String, Object> parseArguments(String argumentsJson) {
        if (argumentsJson == null || argumentsJson.isBlank()) return new LinkedHashMap<>();
        try {
            Object raw = JsonUtils.fromJson(argumentsJson, Object.class);
            if (raw instanceof Map m) return m;
        } catch (Exception e) {
            log.warn("[SkillExecutor] Failed to parse arguments JSON: {}", argumentsJson);
        }
        return new LinkedHashMap<>();
    }

    /**
     * 合成给 Specialist 帧的"用户消息"文本。
     *
     * <p>优先逻辑：
     * <ol>
     *   <li>如果 inputArgs 有 instruction 字段（兜底 schema）→ 直接用</li>
     *   <li>否则，把整个 inputArgs 序列化为自然语言描述</li>
     * </ol>
     *
     * <p>同时在开头附加 Skill 名称，让 Specialist LLM 更清晰地知道自己的身份定位。
     */
    private String buildInstructionForSpecialist(Map<String, Object> inputArgs, Skill skill) {
        StringBuilder sb = new StringBuilder();
        sb.append("【你现在处于 ").append(nullSafe(skill.getName())).append(" 技能模式】\n\n");
        Object instruction = inputArgs.get("instruction");
        if (instruction != null && !String.valueOf(instruction).isBlank()) {
            sb.append("任务指令：\n").append(instruction);
        } else {
            // 没有 instruction 字段时，把整个参数表描述出来
            sb.append("任务参数：\n");
            for (Map.Entry<String, Object> e : inputArgs.entrySet()) {
                sb.append("- ").append(e.getKey()).append(": ").append(e.getValue()).append("\n");
            }
        }
        if (skill.getDescription() != null && !skill.getDescription().isBlank()) {
            sb.append("\n\n技能说明供参考：").append(skill.getDescription().trim());
        }
        sb.append("\n\n请直接执行任务，不要询问用户；需要外部能力时通过工具调用完成。");
        return sb.toString();
    }

    private static String nullSafe(String s) { return s == null ? "" : s; }

    private ConversationContext.ToolResultRecord fail(ConversationContext.ToolCallRecord call, String msg) {
        return new ConversationContext.ToolResultRecord(
                call.toolName(), call.toolCallId(), null, false, msg, null);
    }

    // ── 完成钩子 & SkillUsage（与 SkillCompletionHookStage 语义一致） ──

    private void runCompletionHook(ConversationContext ctx, Skill skill, String llmResponse) {
        switch (skill.getCompletionHookType()) {
            case CREATE_NOTE -> runCreateNote(ctx, skill, llmResponse);
            case SCHEDULE_REMINDER -> log.warn("[SkillExecutor] SCHEDULE_REMINDER not implemented (skill={}, params={})",
                    skill.getName(), skill.getCompletionHookParamsJson());
            case SAVE_TO_KB -> log.warn("[SkillExecutor] SAVE_TO_KB not implemented (skill={}, params={})",
                    skill.getName(), skill.getCompletionHookParamsJson());
            case NONE -> log.debug("[SkillExecutor] Hook=NONE for skill={}", skill.getName());
        }
    }

    private void runCreateNote(ConversationContext ctx, Skill skill, String llmResponse) {
        if (llmResponse == null || llmResponse.isBlank()) {
            log.warn("[SkillExecutor] CREATE_NOTE skipped: empty LLM response (skill={})", skill.getName());
            return;
        }
        String title = skill.getName() + " · " + LocalDateTime.now().toLocalTime()
                .withSecond(0).withNano(0);
        CreateNoteRequest req = new CreateNoteRequest();
        req.setTitle(title);
        req.setContent(llmResponse);
        req.setCategory("Skill 产出");
        req.setPinned(false);
        noteService.createNote(ctx.getUserId(), req);
        log.info("[SkillExecutor] CREATE_NOTE saved: skill={}, title={}, contentLen={}",
                skill.getName(), title, llmResponse.length());
    }

    private void saveSkillUsage(ConversationContext ctx, Skill skill, Map<String, Object> inputArgs,
                                String llmResponse, SkillUsage.UsageStatus status,
                                String errorMessage, int iterations) {
        String inputsJson;
        try {
            inputsJson = JsonUtils.toJson(inputArgs);
        } catch (Exception e) {
            inputsJson = String.valueOf(inputArgs);
        }
        SkillUsage usage = SkillUsage.builder()
                .skillId(skill.getId())
                .skillName(skill.getName())
                .conversationId(ctx.getConversationId())
                .userId(ctx.getUserId())
                .status(status)
                .startedAt(LocalDateTime.now())
                .finishedAt(LocalDateTime.now())
                .inputsSnapshot(truncate(inputsJson, 3800))
                .outputsSnapshot(truncate(llmResponse, 3800))
                .errorMessage(errorMessage != null ? truncate(errorMessage, 1900) : null)
                .agentIterations(iterations)
                .build();
        skillUsageRepository.save(usage);
        log.debug("[SkillExecutor] SkillUsage saved: skill={}, status={}, id={}",
                skill.getId(), status, usage.getId());
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...[truncated]";
    }
}
