package com.example.app.pipeline.stage.agent;

import com.example.app.dto.CreateNoteRequest;
import com.example.app.entity.Skill;
import com.example.app.entity.SkillUsage;
import com.example.app.pipeline.ContextPipelineStage;
import com.example.app.pipeline.context.ConversationContext;
import com.example.app.repository.SkillUsageRepository;
import com.example.app.service.NoteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Agent Skill 完成钩子阶段（POSTPROCESS，order=810）
 *
 * <p>当 ctx.agentState[KEY_ACTIVE_SKILL] 存在时，根据 Skill.completionHookType
 * 执行对应的副作用：
 * <ul>
 *   <li>CREATE_NOTE → 把 LLM 输出写入用户笔记（标题=Skill 名 + 时间戳）</li>
 *   <li>SCHEDULE_REMINDER → MVP 暂未实现，log + no-op（后续接入 ReminderService）</li>
 *   <li>SAVE_TO_KB → MVP 暂未实现，log + no-op（后续接入 KnowledgeBaseService）</li>
 *   <li>NONE → 跳过</li>
 * </ul>
 *
 * <p>无论钩子类型如何，都会写入 SkillUsage 记录（COMPLETED / FAILED），
 * 用于调用历史与统计。
 *
 * <p>角色分支：仅在 ORCHESTRATOR 帧（栈深度=1）执行，避免 Skill 嵌套时重复触发。
 *
 * <p>非关键 Stage：钩子失败不影响主响应，只告警。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SkillCompletionHookStage implements ContextPipelineStage {

    private final NoteService noteService;
    private final SkillUsageRepository skillUsageRepository;

    @Override
    public Phase getPhase() {
        return Phase.POSTPROCESS;
    }

    @Override
    public String getName() {
        return "skillCompletionHookStage";
    }

    @Override
    public void execute(ConversationContext ctx) {
        Skill activeSkill = (Skill) ctx.getAgentState().get(ConversationContext.KEY_ACTIVE_SKILL);
        if (activeSkill == null) {
            log.debug("[SkillCompletionHook] No active skill, skip");
            return;
        }

        // 角色分支：仅在 Orchestrator 帧执行（MVP 单 Skill 模式下栈深度始终=1）
        if (ctx.getAgentStack().depth() > 1) {
            log.debug("[SkillCompletionHook] Skip on nested frame (depth={})",
                    ctx.getAgentStack().depth());
            return;
        }

        String llmResponse = ctx.getLlmResponse();
        SkillUsage.UsageStatus finalStatus = SkillUsage.UsageStatus.COMPLETED;
        String errorMessage = null;

        try {
            executeHook(ctx, activeSkill, llmResponse);
        } catch (Exception e) {
            log.error("[SkillCompletionHook] Hook execution failed for skill '{}': {}",
                    activeSkill.getName(), e.getMessage(), e);
            finalStatus = SkillUsage.UsageStatus.FAILED;
            errorMessage = e.getMessage();
        }

        // 写入 SkillUsage 记录
        try {
            saveSkillUsage(ctx, activeSkill, llmResponse, finalStatus, errorMessage);
        } catch (Exception e) {
            log.warn("[SkillCompletionHook] Failed to save SkillUsage: {}", e.getMessage());
        }

        // 推送 Agent 思考过程：Skill 完成事件
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("skillId", activeSkill.getId());
        data.put("skillName", activeSkill.getName());
        data.put("hookType", activeSkill.getCompletionHookType().name());
        data.put("status", finalStatus.name());
        data.put("responseLength", llmResponse != null ? llmResponse.length() : 0);
        if (errorMessage != null) {
            data.put("errorMessage", errorMessage);
        }
        ctx.emitAgentThinking("skill_completion", data);

        log.info("[SkillCompletionHook] Skill '{}' finished with status={}, hookType={}",
                activeSkill.getName(), finalStatus, activeSkill.getCompletionHookType());
    }

    /**
     * 按 completionHookType 分派执行副作用。
     */
    private void executeHook(ConversationContext ctx, Skill skill, String llmResponse) {
        switch (skill.getCompletionHookType()) {
            case CREATE_NOTE -> executeCreateNote(ctx, skill, llmResponse);
            case SCHEDULE_REMINDER -> {
                log.warn("[SkillCompletionHook] SCHEDULE_REMINDER not implemented yet (skill={}, params={})",
                        skill.getName(), skill.getCompletionHookParamsJson());
            }
            case SAVE_TO_KB -> {
                log.warn("[SkillCompletionHook] SAVE_TO_KB not implemented yet (skill={}, params={})",
                        skill.getName(), skill.getCompletionHookParamsJson());
            }
            case NONE -> log.debug("[SkillCompletionHook] Hook type NONE, skip副作用 (skill={})",
                    skill.getName());
        }
    }

    /**
     * CREATE_NOTE 钩子：把 LLM 输出写入用户笔记。
     *
     * <p>标题：Skill 名 + 时间戳（HH:mm）
     * <p>分类：默认 "Skill 产出"（后续可从 completionHookParamsJson 读取自定义分类）
     * <p>内容：完整 LLM 响应
     */
    private void executeCreateNote(ConversationContext ctx, Skill skill, String llmResponse) {
        if (llmResponse == null || llmResponse.isBlank()) {
            log.warn("[SkillCompletionHook] CREATE_NOTE skipped: empty LLM response (skill={})",
                    skill.getName());
            return;
        }

        String title = skill.getName() + " · " + LocalDateTime.now().toLocalTime()
                .withSecond(0).withNano(0).toString();

        CreateNoteRequest request = new CreateNoteRequest();
        request.setTitle(title);
        request.setContent(llmResponse);
        request.setCategory("Skill 产出");
        request.setPinned(false);

        noteService.createNote(ctx.getUserId(), request);
        log.info("[SkillCompletionHook] CREATE_NOTE saved: skill={}, title={}, contentLen={}",
                skill.getName(), title, llmResponse.length());
    }

    /**
     * 写入 SkillUsage 记录，保留输入/输出快照（截断保护）。
     */
    private void saveSkillUsage(ConversationContext ctx, Skill skill, String llmResponse,
                                 SkillUsage.UsageStatus status, String errorMessage) {
        SkillUsage usage = SkillUsage.builder()
                .skillId(skill.getId())
                .skillName(skill.getName())
                .conversationId(ctx.getConversationId())
                .userId(ctx.getUserId())
                .status(status)
                .startedAt(LocalDateTime.now())
                .finishedAt(LocalDateTime.now())
                .inputsSnapshot(truncate(ctx.getUserMessage(), 3800))
                .outputsSnapshot(truncate(llmResponse, 3800))
                .errorMessage(errorMessage != null ? truncate(errorMessage, 1900) : null)
                .agentIterations(ctx.getCurrentIteration())
                .build();
        skillUsageRepository.save(usage);
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...[truncated]";
    }

    @Override
    public boolean isApplicable(ConversationContext ctx) {
        return ctx.isAgentMode();
    }

    @Override
    public int getOrder() {
        return 810;
    }

    @Override
    public boolean isCritical() {
        return false;
    }
}
