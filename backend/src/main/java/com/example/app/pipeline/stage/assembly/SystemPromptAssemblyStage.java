package com.example.app.pipeline.stage.assembly;

import com.example.app.config.DefaultSystemPrompt;
import com.example.app.dto.QueryAnalysisResult;
import com.example.app.entity.Skill;
import com.example.app.pipeline.ContextPipelineStage;
import com.example.app.pipeline.context.AgentFrame;
import com.example.app.pipeline.context.ConversationContext;
import com.example.app.entity.PromptTemplate;
import com.example.app.service.PromptTemplateService;
import dev.langchain4j.data.message.SystemMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * System Prompt 组装 Stage（ASSEMBLY 阶段，order=410）
 *
 * <p>双层 ReAct 改造 —— 按当前帧角色走完全不同的 prompt 分支：
 *
 * <h3>ORCHESTRATOR 帧（栈深度=1 且 无手动激活 Skill）</h3>
 * 调用 {@link OrchestratorSystemPromptProvider} 构建"任务编排器"专属 system prompt。
 * 不注入任何与原子工具相关的引导词，因为 Orchestrator 层只能看到 Skill 伪 functions。
 *
 * <h3>SPECIALIST 帧（栈深度>1，即 SkillExecutor push 进来的帧）
 * 或 ORCHESTRATOR 帧但 KEY_ACTIVE_SKILL 已设置（旧单 Skill 激活链路回退兼容）</h3>
 * 走 Skill 专属 prompt 逻辑：
 * <ul>
 *   <li>Skill.systemPromptTemplate 非空 → 完全覆盖（仍支持变量替换）</li>
 *   <li>为空 → 默认 prompt 模板 + Skill.systemPromptSupplement 追加</li>
 * </ul>
 *
 * <h3>ORCHESTRATOR 帧 + 无 activeSkill（旧默认通用聊天回退）</h3>
 * 保持原有 executeDefault：默认 prompt 模板 + 用户画像 + 记忆 + 搜索 + KB 引用。
 * 这条路径覆盖非 Agent 模式（纯聊天）、Agent 模式但用户的需求不需要走 Skill（无 Skill 可用的场景）。
 *
 * <p>变量替换顺序与原实现保持一致：{user_profile} {memory_cognee_graph} {search_context}
 * {context_policy} {custom_rules}。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SystemPromptAssemblyStage implements ContextPipelineStage {

    private final PromptTemplateService templateService;
    private final OrchestratorSystemPromptProvider orchestratorPromptProvider;

    @Override
    public Phase getPhase() {
        return Phase.ASSEMBLY;
    }

    public String getName() {
        return "systemPromptAssemblyStage";
    }

    @Override
    public void execute(ConversationContext ctx) {
        AgentFrame currentFrame = ctx.getAgentStack().peek();
        Skill activeSkill = (Skill) ctx.getAgentState().get(ConversationContext.KEY_ACTIVE_SKILL);

        // Branch 1: SPECIALIST 帧 → 走 Skill 专属 prompt
        //   或 ORCHESTRATOR 帧 + activeSkill 非空 = 旧单 Skill 手动激活链路
        //   （用户通过 / 显式选 Skill，ChatService 走 executeWithAgentLoop 而非 OrchestratorLoop）
        //
        // 关键：SkillResolutionStage 已移除关键词匹配，ORCHESTRATOR 帧上的 activeSkill
        //       只可能来自手动 skillId（旧链路），不会污染 Orchestrator 路由路径。
        if (currentFrame.getRole() == AgentFrame.Role.SPECIALIST || activeSkill != null) {
            Skill skillForPrompt = activeSkill;
            // 双重兜底：SPECIALIST 帧但 agentState 没写 activeSkill（极端情况），从 Frame.skillId 再取
            if (skillForPrompt == null && currentFrame.getSkillId() != null) {
                // 这里不直接调 SkillService（避免构造依赖，留给 ToolDefinitionStage 已做过注入），
                // 如果此时为 null 就退化到默认 prompt；正常链路不会发生。
                log.warn("[SystemPrompt] SPECIALIST frame {} has skillId={} but no KEY_ACTIVE_SKILL in agentState, " +
                        "fallback to default prompt", ctx.getAgentStack().currentFrameId(), currentFrame.getSkillId());
            }
            if (skillForPrompt != null) {
                executeWithSkill(ctx, skillForPrompt);
                return;
            }
        }

        // Branch 2: ORCHESTRATOR 帧 + AgentMode → Orchestrator 路由 Prompt
        if (currentFrame.getRole() == AgentFrame.Role.ORCHESTRATOR && ctx.isAgentMode()) {
            executeOrchestrator(ctx);
            return;
        }

        // Branch 3: 回退（非 Agent 模式、或其他情况）
        executeDefault(ctx);
    }

    // ────────────── ORCHESTRATOR 路由 Prompt ─────────────────

    private void executeOrchestrator(ConversationContext ctx) {
        String userProfileText = (String) ctx.getAgentState()
                .getOrDefault(ConversationContext.KEY_FORMATTED_USER_PROFILE, "");
        String kbReferences = (String) ctx.getAgentState()
                .getOrDefault(KnowledgeBaseRetrievalStage.KEY_FORMATTED_KB_REFERENCES, "");
        String customRules = ctx.getCustomRules();
        String customRulesSection = (customRules != null && !customRules.isBlank())
                ? "【会话自定义指令】\n" + customRules.trim() : "";

        // 简单的语言推断：用户消息中如果汉字占比高，用 zh-CN，否则留空让 provider 用默认
        String userLanguage = guessLanguageFromMessage(ctx.getUserMessage());

        String systemPrompt = orchestratorPromptProvider.build(
                userLanguage, customRulesSection, userProfileText, kbReferences);

        // 搜索上下文对 Orchestrator 意义不大（它只做路由不查资料，查资料由 Skill 内部自己调用），
        // 这里不注入 {search_context} 以减少 token 占用。Cognee 记忆同样不在 Orchestrator 层注入。
        // 如果 future 场景需要，再加回来。

        ctx.getAgentState().put(ConversationContext.KEY_SYSTEM_MESSAGE, SystemMessage.from(systemPrompt));
        ctx.getAgentState().put(ConversationContext.KEY_PROMPT_TEMPLATE_VERSION, -99); // 标识 Orchestrator 自定义

        log.info("[SystemPrompt][ORCH] role=ORCHESTRATOR promptLen={} userLang={}",
                systemPrompt.length(), userLanguage);
    }

    private static String guessLanguageFromMessage(String msg) {
        if (msg == null || msg.isBlank()) return null;
        int chinese = 0;
        for (int i = 0; i < msg.length(); i++) {
            char c = msg.charAt(i);
            if (c >= 0x4E00 && c <= 0x9FFF) chinese++;
        }
        return (chinese * 10 >= msg.length() * 3) ? "zh-CN" : null;
    }

    // ────────────── Skill 专家 Prompt（SPECIALIST / 旧单 Skill 回退） ──

    private void executeWithSkill(ConversationContext ctx, Skill skill) {
        String searchText = (String) ctx.getAgentState().getOrDefault(ConversationContext.KEY_FORMATTED_SEARCH, "");
        String userProfileText = (String) ctx.getAgentState()
                .getOrDefault(ConversationContext.KEY_FORMATTED_USER_PROFILE, "");
        String cogneeGraph = (String) ctx.getAgentState()
                .getOrDefault(ConversationContext.KEY_FORMATTED_MEMORY_COGNEE, "");
        String contextPolicy = buildContextPolicy(ctx.getQueryAnalysisResult());
        String customRules = ctx.getCustomRules();
        String customRulesSection = (customRules != null && !customRules.isBlank())
                ? "【会话自定义指令】\n" + customRules.trim() : "";

        Map<String, String> params = new HashMap<>();
        params.put("user_profile", userProfileText);
        params.put("memory_cognee_graph", blankToNone(cogneeGraph));
        params.put("context_policy", contextPolicy);
        params.put("search_context", searchText);
        params.put("custom_rules", customRulesSection);

        String systemPrompt;
        String template = skill.getSystemPromptTemplate();
        if (template != null && !template.isBlank()) {
            systemPrompt = renderInline(template, params);
            log.info("[SystemPrompt][SPEC/Skill] Using skill template: skillId={}, name={}, length={}",
                    skill.getId(), skill.getName(), systemPrompt.length());
        } else {
            systemPrompt = renderDefault(ctx, params, userProfileText, cogneeGraph,
                    contextPolicy, searchText, customRulesSection);
            String supplement = skill.getSystemPromptSupplement();
            if (supplement != null && !supplement.isBlank()) {
                systemPrompt = systemPrompt.trim() + "\n\n【Skill 补充指令】\n" + supplement.trim();
            }
            log.info("[SystemPrompt][SPEC/Skill] Using default + supplement: skillId={}, name={}, supplementLen={}",
                    skill.getId(), skill.getName(), supplement != null ? supplement.length() : 0);
        }

        String kbReferences = (String) ctx.getAgentState()
                .getOrDefault(KnowledgeBaseRetrievalStage.KEY_FORMATTED_KB_REFERENCES, "");
        if (kbReferences != null && !kbReferences.isBlank()) {
            systemPrompt = systemPrompt.trim() + "\n\n" + kbReferences.trim();
        }

        ctx.getAgentState().put(ConversationContext.KEY_PROMPT_TEMPLATE_VERSION, -1);
        ctx.getAgentState().put(ConversationContext.KEY_SYSTEM_MESSAGE, SystemMessage.from(systemPrompt));
        ctx.getAgentState().put("contextPolicy", contextPolicy);
        ctx.getAgentState().put("customRules", customRulesSection);
    }

    // ────────────── 默认 Prompt（非 Agent、或回退路径） ─────

    private void executeDefault(ConversationContext ctx) {
        String searchText = (String) ctx.getAgentState().getOrDefault(ConversationContext.KEY_FORMATTED_SEARCH, "");
        String userProfileText = (String) ctx.getAgentState()
                .getOrDefault(ConversationContext.KEY_FORMATTED_USER_PROFILE, "");
        String cogneeGraph = (String) ctx.getAgentState()
                .getOrDefault(ConversationContext.KEY_FORMATTED_MEMORY_COGNEE, "");
        String contextPolicy = buildContextPolicy(ctx.getQueryAnalysisResult());
        String customRules = ctx.getCustomRules();
        String customRulesSection = (customRules != null && !customRules.isBlank())
                ? "【会话自定义指令】\n" + customRules.trim() : "";

        Map<String, String> params = new HashMap<>();
        params.put("user_profile", userProfileText);
        params.put("memory_cognee_graph", blankToNone(cogneeGraph));
        params.put("context_policy", contextPolicy);
        params.put("search_context", searchText);
        params.put("custom_rules", customRulesSection);

        String systemPrompt = renderDefault(ctx, params, userProfileText, cogneeGraph,
                contextPolicy, searchText, customRulesSection);

        String kbReferences = (String) ctx.getAgentState()
                .getOrDefault(KnowledgeBaseRetrievalStage.KEY_FORMATTED_KB_REFERENCES, "");
        if (kbReferences != null && !kbReferences.isBlank()) {
            systemPrompt = systemPrompt.trim() + "\n\n" + kbReferences.trim();
        }

        ctx.getAgentState().put(ConversationContext.KEY_SYSTEM_MESSAGE, SystemMessage.from(systemPrompt));
        ctx.getAgentState().put("contextPolicy", contextPolicy);
        ctx.getAgentState().put("customRules", customRulesSection);

        Object templateVersion = ctx.getAgentState().get(ConversationContext.KEY_PROMPT_TEMPLATE_VERSION);
        log.debug("[SystemPrompt] templateVersion={}, cognee='{}', policy='{}'",
                templateVersion, truncate(cogneeGraph, 30), truncate(contextPolicy, 50));
    }

    private String renderDefault(ConversationContext ctx, Map<String, String> params,
                                  String userProfileText, String cogneeGraph,
                                  String contextPolicy, String searchText,
                                  String customRulesSection) {
        String systemPrompt;
        int templateVersion = -1;
        try {
            systemPrompt = templateService.renderTemplate("default-system-prompt", params);
            templateVersion = templateService.findActiveLatestVersion("default-system-prompt")
                    .map(PromptTemplate::getVersion)
                    .orElse(-1);
        } catch (IllegalArgumentException e) {
            log.warn("Template not found, using fallback: {}", e.getMessage());
            systemPrompt = DefaultSystemPrompt.CONTENT
                    .replace("{user_profile}", userProfileText)
                    .replace("{memory_cognee_graph}", blankToNone(cogneeGraph))
                    .replace("{custom_rules}", customRulesSection)
                    .replace("{context_policy}", contextPolicy)
                    .replace("{search_context}", searchText);
            templateVersion = -1;
        }
        if (systemPrompt == null || systemPrompt.isBlank()) {
            systemPrompt = "你是一个智能助手。请根据上下文回答问题。";
        }
        ctx.getAgentState().put(ConversationContext.KEY_PROMPT_TEMPLATE_VERSION, templateVersion);
        return systemPrompt;
    }

    private String renderInline(String template, Map<String, String> params) {
        String result = template;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue() != null ? entry.getValue() : "");
        }
        return result;
    }

    private String buildContextPolicy(QueryAnalysisResult analysis) {
        if (analysis == null || analysis.getIntentType() == null) return "";
        return switch (analysis.getIntentType()) {
            case KNOWLEDGE_QUERY ->
                    "【上下文策略】本轮为知识询问，请优先基于当前问题和相关知识图谱中的信息回答，不要引用与问题无关的历史对话。";
            case PROFILE_QUERY ->
                    "【上下文策略】本轮为用户档案查询，请优先使用用户档案中的信息回答，确保信息准确。";
            case TASK_EXECUTION ->
                    "【上下文策略】本轮为任务执行，请结合精确记忆中的项目/任务信息，专注完成当前任务。";
            case CONTEXT_DEPENDENT ->
                    "【上下文策略】本轮依赖上下文，请结合对话历史回答，特别注意代词指代的消解（这个、那个、刚才等）。";
            case CHAT_SMALLTALK ->
                    "【上下文策略】本轮为闲聊，请用友好简洁的方式回应，无需引用历史信息。";
            case MATH_CALCULATION ->
                    "【上下文策略】本轮为数学计算，直接给出计算结果，无需引用历史。";
            case GENERAL -> "";
        };
    }

    private String blankToNone(String s) { return (s == null || s.isBlank()) ? "无" : s; }
    private String truncate(String s, int maxLen) {
        if (s == null || s.isBlank()) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }

    @Override
    public int getOrder() { return 410; }
}
