package com.example.app.pipeline.stage.assembly;

import com.example.app.config.DefaultSystemPrompt;
import com.example.app.dto.QueryAnalysisResult;
import com.example.app.pipeline.ContextPipelineStage;
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
 * <p>v5 改造：语言并入用户档案、移除档案双源（{@code memory_l1_profile} 废弃）
 * <ul>
 *   <li>{user_profile}: 用户档案（设置表，含语言偏好）</li>
 *   <li>{memory_cognee_graph}: 相关知识图谱 (Cognee, 片段+实体+关系)</li>
 *   <li>{memory_l3_preference}: 用户偏好 (JPA L3, PREFERENCE/SKILL/RULE)</li>
 *   <li>{memory_precise}: 精确记忆 (JPA L2, FACT/KNOWLEDGE)</li>
 *   <li>{context_policy}: 根据意图类型动态注入的上下文使用策略指令</li>
 * </ul>
 *
 * <p>注入顺序：用户档案 → 搜索上下文 → 知识图谱 → 用户偏好 → 精确记忆
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SystemPromptAssemblyStage implements ContextPipelineStage {

    private final PromptTemplateService templateService;

    @Override
    public Phase getPhase() {
        return Phase.ASSEMBLY;
    }

    public String getName() {
        return "systemPromptAssemblyStage";
    }

    @Override
    public void execute(ConversationContext ctx) {
        String searchText = (String) ctx.getAgentState().getOrDefault(ConversationContext.KEY_FORMATTED_SEARCH, "");
        String userProfileText = (String) ctx.getAgentState()
                .getOrDefault(ConversationContext.KEY_FORMATTED_USER_PROFILE, "");

        // Memory blocks (v5)：L1 档案块已废弃（由 user_profile 设置表承担），仅注入图谱/偏好/精确
        String cogneeGraph = (String) ctx.getAgentState()
                .getOrDefault(ConversationContext.KEY_FORMATTED_MEMORY_COGNEE, "");
        String l3Preference = (String) ctx.getAgentState()
                .getOrDefault(ConversationContext.KEY_FORMATTED_MEMORY_L3, "");
        String preciseMemory = (String) ctx.getAgentState()
                .getOrDefault(ConversationContext.KEY_FORMATTED_MEMORY_PRECISE, "");

        // Legacy combined (v2) — for template backward compat
        String legacyMemory = (String) ctx.getAgentState()
                .getOrDefault(ConversationContext.KEY_FORMATTED_MEMORY, "");

        // context_policy: 根据意图类型动态生成
        String contextPolicy = buildContextPolicy(ctx.getQueryAnalysisResult());

        // 会话级自定义规则（由 ConversationRulesLoadStage 加载）
        String customRules = ctx.getCustomRules();
        String customRulesSection = (customRules != null && !customRules.isBlank())
                ? "【会话自定义指令】\n" + customRules.trim()
                : "";

        Map<String, String> params = new HashMap<>();
        params.put("user_profile", userProfileText);
        params.put("memory_cognee_graph", blankToNone(cogneeGraph));
        params.put("memory_l3_preference", blankToNone(l3Preference));
        params.put("memory_precise", blankToNone(preciseMemory));
        params.put("context_policy", contextPolicy);
        params.put("search_context", searchText);
        params.put("custom_rules", customRulesSection);

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
                    .replace("{memory_l3_preference}", blankToNone(l3Preference))
                    .replace("{memory_precise}", blankToNone(preciseMemory))
                    .replace("{custom_rules}", customRulesSection)
                    .replace("{context_policy}", contextPolicy)
                    .replace("{search_context}", searchText);
            templateVersion = -1;
        }

        // 降级：如果模板不含新占位符，用旧格式兜底
        if (systemPrompt.contains("{long_term_memory}")) {
            systemPrompt = systemPrompt.replace("{long_term_memory}", blankToNone(legacyMemory));
        }

        if (systemPrompt == null || systemPrompt.isBlank()) {
            systemPrompt = "你是一个智能助手。请根据上下文回答问题。";
        }

        // 追加用户显式引用的知识库上下文段（KnowledgeBaseRetrievalStage 408 写入）
        String kbReferences = (String) ctx.getAgentState()
                .getOrDefault(KnowledgeBaseRetrievalStage.KEY_FORMATTED_KB_REFERENCES, "");
        if (kbReferences != null && !kbReferences.isBlank()) {
            systemPrompt = systemPrompt.trim() + "\n\n" + kbReferences.trim();
        }

        ctx.getAgentState().put(ConversationContext.KEY_PROMPT_TEMPLATE_VERSION, templateVersion);
        ctx.getAgentState().put(ConversationContext.KEY_SYSTEM_MESSAGE, SystemMessage.from(systemPrompt));
        ctx.getAgentState().put("contextPolicy", contextPolicy);
        ctx.getAgentState().put("customRules", customRulesSection);

        log.debug("[SystemPrompt] templateVersion={}, cognee='{}', l3='{}', precise='{}', policy='{}'",
                templateVersion,
                truncate(cogneeGraph, 30),
                truncate(l3Preference, 30),
                truncate(preciseMemory, 30),
                truncate(contextPolicy, 50));
    }

    /**
     * 根据意图类型构建 context_policy 指令
     */
    private String buildContextPolicy(QueryAnalysisResult analysis) {
        if (analysis == null || analysis.getIntentType() == null) {
            return "";
        }

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

    private String blankToNone(String s) {
        return (s == null || s.isBlank()) ? "无" : s;
    }

    private String truncate(String s, int maxLen) {
        if (s == null || s.isBlank()) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }

    @Override
    public int getOrder() {
        return 410;
    }
}
