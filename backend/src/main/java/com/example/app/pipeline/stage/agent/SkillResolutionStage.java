package com.example.app.pipeline.stage.agent;

import com.example.app.entity.Skill;
import com.example.app.pipeline.ContextPipelineStage;
import com.example.app.pipeline.context.AgentFrame;
import com.example.app.pipeline.context.ConversationContext;
import com.example.app.service.SkillService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Agent Skill 解析阶段（PREPROCESS，order=330）
 *
 * <p>根据请求参数和用户消息匹配激活的 Skill，写入 ctx.agentState[KEY_ACTIVE_SKILL]
 * 和 ctx.activeSkillId，供后续 Stage（SystemPromptAssemblyStage / ToolDefinitionStage /
 * SkillCompletionHookStage）读取。
 *
 * <p>匹配优先级：
 * <ol>
 *   <li>手动指定（ChatRequest.skillId 非空）→ 直接激活</li>
 *   <li>关键词匹配（用户消息命中 Skill.triggerKeywords）→ 第一个命中</li>
 *   <li>无匹配 → 不激活，走默认通用模式</li>
 * </ol>
 *
 * <p>角色分支（栈帧隔离）：
 * <ul>
 *   <li>ORCHESTRATOR（栈深度=1）：执行匹配逻辑</li>
 *   <li>SPECIALIST（栈深度&gt;1）：跳过（Skill 进入时身份已定，无需再次解析）</li>
 * </ul>
 *
 * <p>isApplicable 仅检查 Agent 模式（静态配置），栈深度判断放在 execute() 内，
 * 避免动态数据被提前过滤。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SkillResolutionStage implements ContextPipelineStage {

    private final SkillService skillService;

    @Override
    public Phase getPhase() {
        return Phase.PREPROCESS;
    }

    @Override
    public String getName() {
        return "skillResolutionStage";
    }

    @Override
    public void execute(ConversationContext ctx) {
        // 角色分支：SPECIALIST 帧跳过（身份在 push 时已确定）
        AgentFrame currentFrame = ctx.getAgentStack().peek();
        if (currentFrame.getRole() != AgentFrame.Role.ORCHESTRATOR) {
            log.debug("[SkillResolution] Skip on non-orchestrator frame (role={}, skillId={})",
                    currentFrame.getRole(), currentFrame.getSkillId());
            return;
        }

        String userId = ctx.getUserId();
        String skillId = ctx.getSkillId();

        // 仅手动指定 skillId 时激活（旧单 Skill 链路：用户通过 / 显式选 Skill → executeWithAgentLoop）。
        // 关键词匹配已移除 —— Orchestrator LLM 通过 call_skill_* 伪函数自行路由，
        // 不再需要 PREPROCESS 阶段的启发式关键词匹配。
        if (skillId == null || skillId.isBlank()) {
            log.debug("[SkillResolution] No manual skillId, Orchestrator LLM will handle routing");
            return;
        }

        Skill skill = skillService.matchActiveSkill(userId, skillId, null);
        if (skill == null) {
            log.debug("[SkillResolution] Manual skillId={} not found or disabled", skillId);
            return;
        }

        // 写入 agentState + activeSkillId，供下游 Stage 读取
        ctx.getAgentState().put(ConversationContext.KEY_ACTIVE_SKILL, skill);
        ctx.setActiveSkillId(skill.getId());

        // 推送 Agent 思考过程：Skill 激活事件
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("skillId", skill.getId());
        data.put("skillName", skill.getName());
        data.put("description", skill.getDescription());
        data.put("matchMode", "MANUAL");
        data.put("maxIterations", skill.getMaxIterations());
        ctx.emitAgentThinking("skill_resolution", data);

        log.info("[SkillResolution] Active skill (manual): id={}, name={}",
                skill.getId(), skill.getName());
    }

    @Override
    public boolean isApplicable(ConversationContext ctx) {
        return ctx.isAgentMode();
    }

    @Override
    public int getOrder() {
        return 330;
    }

    @Override
    public boolean isCritical() {
        return false;
    }
}
