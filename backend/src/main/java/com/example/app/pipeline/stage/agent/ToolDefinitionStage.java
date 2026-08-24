package com.example.app.pipeline.stage.agent;

import com.example.app.entity.Skill;
import com.example.app.pipeline.ContextPipelineStage;
import com.example.app.pipeline.context.AgentFrame;
import com.example.app.pipeline.context.ConversationContext;
import com.example.app.pipeline.stage.agent.tool.SkillToolSpecFactory;
import com.example.app.service.SkillService;
import com.example.app.service.tool.ToolSpecificationProvider;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolSpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 工具定义注入阶段（ASSEMBLY，order=480）
 *
 * <p>双层 ReAct 改造后，按当前帧角色分支提供完全不同的 tool list：
 *
 * <h3>ORCHESTRATOR 帧（栈深度=1）</h3>
 * 顶层编排者 <b>看不到任何原子 Tool</b>（如 createReminder、webSearch 等）。
 * 它只能看到一组由 Skill 编译而来的"伪 function"：
 * {@code call_skill_<skillId>({instruction, ...})}，由 {@link SkillToolSpecFactory} 构建。
 * 这样 Orchestrator 的 LLM 就只能在 Skill 粒度做路由决策，不能"跳过 Skill 直接干活"。
 *
 * <h3>SPECIALIST 帧（栈深度>1）</h3>
 * Skill 专家帧 <b>看不到其他 Skill 伪函数</b>，只能看到当前 Skill 白/黑名单过滤后的
 * 原子 Tool 列表。保持原有行为：allowedToolNames（白名单优先）+ forbiddenToolNames（叠加排除）。
 *
 * <p>KB 引用过滤（recallMemory）：两条分支都生效，保持原有兼容性。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ToolDefinitionStage implements ContextPipelineStage {

    /** Key for tool specifications in agentState, read by ModelRoutingStage(500) */
    public static final String KEY_TOOL_SPECIFICATIONS = "toolSpecifications";

    private final ToolSpecificationProvider toolSpecificationProvider;
    private final SkillService skillService;
    private final ObjectMapper objectMapper;

    private static final TypeReference<List<String>> LIST_STRING = new TypeReference<>() {};

    @Override
    public Phase getPhase() {
        return Phase.ASSEMBLY;
    }

    @Override
    public String getName() {
        return "toolDefinitionStage";
    }

    @Override
    public void execute(ConversationContext ctx) {
        AgentFrame currentFrame = ctx.getAgentStack().peek();
        Skill activeSkill = (Skill) ctx.getAgentState().get(ConversationContext.KEY_ACTIVE_SKILL);

        List<ToolSpecification> specs;
        Map<String, Object> traceData = new LinkedHashMap<>();
        traceData.put("frameId", ctx.getAgentStack().currentFrameId());
        traceData.put("role", currentFrame.getRole().name());

        // 判断是否走 SPECIALIST 过滤分支：
        //   a) 帧角色是 SPECIALIST（SkillExecutor 嵌套 push 的帧）→ 始终 specialist
        //   b) ORCHESTRATOR 帧 + KEY_ACTIVE_SKILL 已设置 = 旧"单 Skill 手动激活"链路
        //      （用户通过 / 手动选 Skill，ChatService 走 executeWithAgentLoop 而非 OrchestratorLoop）
        //   c) ORCHESTRATOR 帧 + 无 activeSkill = Orchestrator 路由模式 → 只暴露 call_skill_* 伪函数
        //
        // 关键：SkillResolutionStage 已移除关键词匹配，ORCHESTRATOR 帧上的 activeSkill
        //       只可能来自手动 skillId（旧链路），不会污染 Orchestrator 路由路径。
        boolean specialistMode = (currentFrame.getRole() == AgentFrame.Role.SPECIALIST)
                || (activeSkill != null);

        if (specialistMode) {
            specs = buildSpecialistAtomicTools(ctx, currentFrame);
            traceData.put("mode", "SPECIALIST (filtered atomic tools)");
            if (activeSkill != null) traceData.put("skillId", activeSkill.getId());
            else if (currentFrame.getSkillId() != null) traceData.put("skillId", currentFrame.getSkillId());
        } else {
            specs = buildOrchestratorSkillPseudoTools(ctx);
            traceData.put("mode", "ORCHESTRATOR (skill pseudo-functions)");
        }

        ctx.getEnabledToolNames().clear();
        for (ToolSpecification spec : specs) {
            ctx.getEnabledToolNames().add(spec.name());
        }
        ctx.getAgentState().put(KEY_TOOL_SPECIFICATIONS, specs);
        traceData.put("tools", ctx.getEnabledToolNames());
        traceData.put("count", specs.size());

        log.info("[ToolDefinition] frameId={} role={} : {} tool(s) enabled: {}",
                ctx.getAgentStack().currentFrameId(),
                currentFrame.getRole(),
                specs.size(),
                ctx.getEnabledToolNames());
        ctx.emitAgentThinking("tool_definition", traceData);
    }

    /**
     * ORCHESTRATOR 帧专用：从 SkillService 取用户可见的全部启用 Skill，
     * 用 SkillToolSpecFactory 编译为 call_skill_* 伪 ToolSpecification 列表。
     */
    private List<ToolSpecification> buildOrchestratorSkillPseudoTools(ConversationContext ctx) {
        List<Skill> skills = skillService.listEnabledForUser(ctx.getUserId());
        List<ToolSpecification> pseudoSpecs = SkillToolSpecFactory.build(skills);
        log.info("[ToolDefinition][ORCH] Compiled {} skill(s) → {} pseudo-tool spec(s)",
                skills.size(), pseudoSpecs.size());
        return pseudoSpecs;
    }

    /**
     * SPECIALIST 帧专用：取全部原子 Tool → (KB 过滤) → Skill 白/黑名单过滤。
     */
    private List<ToolSpecification> buildSpecialistAtomicTools(ConversationContext ctx,
                                                               AgentFrame currentFrame) {
        List<ToolSpecification> specs = toolSpecificationProvider.getToolSpecifications(ctx.getUserId());

        boolean hasExplicitKbRefs = ctx.getKnowledgeBaseIds() != null && !ctx.getKnowledgeBaseIds().isEmpty();
        if (hasExplicitKbRefs) {
            // 用户显式 @ 了知识库 → KnowledgeBaseRetrievalStage(408) 已把指定库片段注入 system prompt：
            // - recallMemory ：查 main_dataset，绕过"只看指定知识库"的用户意图，过滤
            // - searchAllKb ：跨所有知识库搜，同样绕过指定限制，过滤
            // - searchInKb  ：同库重复检索，片段已注入上下文，过滤
            specs = specs.stream()
                    .filter(spec -> !"recallMemory".equals(spec.name()))
                    .filter(spec -> !"searchAllKb".equals(spec.name()))
                    .filter(spec -> !"searchInKb".equals(spec.name()))
                    .toList();
        }

        Skill activeSkill = (Skill) ctx.getAgentState().get(ConversationContext.KEY_ACTIVE_SKILL);
        if (activeSkill == null && currentFrame.getSkillId() != null) {
            activeSkill = skillService.getEntityById(currentFrame.getSkillId());
            if (activeSkill != null) {
                ctx.getAgentState().put(ConversationContext.KEY_ACTIVE_SKILL, activeSkill);
            }
        }
        if (activeSkill != null) {
            specs = filterBySkill(specs, activeSkill);
            log.info("[ToolDefinition][SPEC] Filtered by skill '{}': {} tool(s) remaining",
                    activeSkill.getName(), specs.size());
        } else {
            log.warn("[ToolDefinition][SPEC] No activeSkill bound for specialist frame skillId={}, " +
                    "fallback to all atomic tools", currentFrame.getSkillId());
        }
        return specs;
    }

    /**
     * 按 Skill 的白/黑名单过滤工具列表。
     *
     * <p>过滤规则：
     * <ul>
     *   <li>allowedToolNames 非空 → 只保留白名单内工具</li>
     *   <li>forbiddenToolNames 非空 → 从结果中排除</li>
     * </ul>
     * 两者可叠加：先白名单收敛，再黑名单排除。
     */
    private List<ToolSpecification> filterBySkill(List<ToolSpecification> specs, Skill skill) {
        List<String> allowed = fromJsonList(skill.getAllowedToolNamesJson());
        List<String> forbidden = fromJsonList(skill.getForbiddenToolNamesJson());

        List<ToolSpecification> filtered = specs;
        if (!allowed.isEmpty()) {
            filtered = filtered.stream()
                    .filter(spec -> allowed.contains(spec.name()))
                    .toList();
            log.debug("[ToolDefinition][Skill] Whitelist filter: allowed={}, after={}",
                    allowed, filtered.stream().map(ToolSpecification::name).toList());
        }
        if (!forbidden.isEmpty()) {
            filtered = filtered.stream()
                    .filter(spec -> !forbidden.contains(spec.name()))
                    .toList();
            log.debug("[ToolDefinition][Skill] Blacklist filter: forbidden={}, after={}",
                    forbidden, filtered.stream().map(ToolSpecification::name).toList());
        }
        return filtered;
    }

    private List<String> fromJsonList(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, LIST_STRING);
        } catch (Exception e) {
            log.warn("[ToolDefinition] Failed to deserialize list: {}", json, e);
            return Collections.emptyList();
        }
    }

    @Override
    public boolean isApplicable(ConversationContext ctx) {
        return ctx.isAgentMode();
    }

    @Override
    public int getOrder() {
        return 480;
    }

    @Override
    public boolean isCritical() {
        return false;
    }
}
