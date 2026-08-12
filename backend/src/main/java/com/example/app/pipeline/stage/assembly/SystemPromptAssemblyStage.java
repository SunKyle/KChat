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
 * <p>v3 改造：支持分层记忆注入 + context_policy 动态指令
 * <ul>
 *   <li>{memory_l1_profile}: 用户档案（始终注入）</li>
 *   <li>{memory_l2_relevant}: 当前问题相关记忆（动态注入）</li>
 *   <li>{memory_l3_preference}: 用户偏好（可选注入）</li>
 *   <li>{context_policy}: 根据意图类型动态注入的上下文使用策略指令</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SystemPromptAssemblyStage implements ContextPipelineStage {

    private final PromptTemplateService templateService;

    private static final Map<String, String> LANGUAGE_NAMES = Map.ofEntries(
            Map.entry("zh-CN", "中文（简体）"),
            Map.entry("zh-TW", "中文（繁體）"),
            Map.entry("en", "English"),
            Map.entry("en-US", "English"),
            Map.entry("en-GB", "English"),
            Map.entry("ja", "日本語"),
            Map.entry("ko", "한국어"),
            Map.entry("fr", "Français"),
            Map.entry("de", "Deutsch"),
            Map.entry("es", "Español"),
            Map.entry("ru", "Русский"));

    @Override
    public Phase getPhase() {
        return Phase.ASSEMBLY;
    }

    public String getName() {
        return "systemPromptAssemblyStage";
    }

    @Override
    public void execute(ConversationContext ctx) {
        String languageClause = buildLanguageClause(ctx.getLanguage());
        String searchText = (String) ctx.getAgentState().getOrDefault(ConversationContext.KEY_FORMATTED_SEARCH, "");
        String userProfileText = (String) ctx.getAgentState()
                .getOrDefault(ConversationContext.KEY_FORMATTED_USER_PROFILE, "");

        // 分层记忆（v3）
        String l1Profile = (String) ctx.getAgentState()
                .getOrDefault(ConversationContext.KEY_FORMATTED_MEMORY_L1, "");
        String l2Relevant = (String) ctx.getAgentState()
                .getOrDefault(ConversationContext.KEY_FORMATTED_MEMORY_L2, "");
        String l3Preference = (String) ctx.getAgentState()
                .getOrDefault(ConversationContext.KEY_FORMATTED_MEMORY_L3, "");

        // 兼容旧格式（v2）
        String legacyMemory = (String) ctx.getAgentState()
                .getOrDefault(ConversationContext.KEY_FORMATTED_MEMORY, "");

        // context_policy：根据意图类型动态生成
        String contextPolicy = buildContextPolicy(ctx.getQueryAnalysisResult());

        // 会话级自定义规则（由 ConversationRulesLoadStage 加载）
        String customRules = ctx.getCustomRules();
        String customRulesSection = (customRules != null && !customRules.isBlank())
                ? "【会话自定义指令】\n" + customRules.trim()
                : "";

        Map<String, String> params = new HashMap<>();
        params.put("language_clause", languageClause);
        params.put("user_profile", userProfileText);
        params.put("memory_l1_profile", blankToNone(l1Profile));
        params.put("memory_l2_relevant", blankToNone(l2Relevant));
        params.put("memory_l3_preference", blankToNone(l3Preference));
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
                    .replace("{language_clause}", languageClause)
                    .replace("{user_profile}", userProfileText)
                    .replace("{memory_l1_profile}", blankToNone(l1Profile))
                    .replace("{memory_l2_relevant}", blankToNone(l2Relevant))
                    .replace("{memory_l3_preference}", blankToNone(l3Preference))
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

        ctx.getAgentState().put(ConversationContext.KEY_PROMPT_TEMPLATE_VERSION, templateVersion);
        ctx.getAgentState().put(ConversationContext.KEY_SYSTEM_MESSAGE, SystemMessage.from(systemPrompt));

        log.debug("[SystemPrompt] templateVersion={}, l1Profile='{}', l2Relevant='{}', l3Preference='{}', policy='{}'",
                templateVersion,
                truncate(l1Profile, 30),
                truncate(l2Relevant, 30),
                truncate(l3Preference, 30),
                truncate(contextPolicy, 50));
    }

    /**
     * 根据意图类型构建 context_policy 指令
     *
     * <p>告诉模型在当前对话中如何使用历史上下文：
     * <ul>
     *   <li>独立问题：仅基于当前提问回答，不要引用历史</li>
     *   <li>上下文依赖：结合对话历史，特别注意指代消解</li>
     *   <li>档案查询：优先使用用户档案中的信息</li>
     *   <li>闲聊：仅用昵称打招呼即可</li>
     * </ul>
     */
    private String buildContextPolicy(QueryAnalysisResult analysis) {
        if (analysis == null || analysis.getIntentType() == null) {
            return "";
        }

        return switch (analysis.getIntentType()) {
            case KNOWLEDGE_QUERY ->
                    "【上下文策略】本轮为知识询问，请优先基于当前问题和相关记忆中的信息回答，不要引用与问题无关的历史对话。";
            case PROFILE_QUERY ->
                    "【上下文策略】本轮为用户档案查询，请优先使用用户档案中的信息回答，确保信息准确。";
            case TASK_EXECUTION ->
                    "【上下文策略】本轮为任务执行，请结合相关记忆中的项目/任务信息，专注完成当前任务。";
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

    private String buildLanguageClause(String language) {
        if (language == null || language.isBlank()) {
            return "";
        }
        String languageName = LANGUAGE_NAMES.getOrDefault(language, language);
        return "请使用 " + languageName + " 回复。";
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