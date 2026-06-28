package com.example.app.pipeline.stage.assembly;

import com.example.app.pipeline.ContextPipelineStage;
import com.example.app.pipeline.context.ConversationContext;
import com.example.app.service.PromptTemplateService;
import dev.langchain4j.data.message.SystemMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

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

    private static final String FALLBACK_TEMPLATE = """
            角色：你是 KChat 智能助手，一个专业、友好的AI助手。

            核心指令：
            1. 始终使用 {language_clause}
            2. 基于提供的用户背景信息回答问题
            3. 回答要简洁明了，避免冗长
            4. 对于不确定的问题，诚实告知

            {long_term_memory}
            {search_context}
            开始回答：
            """;

    @Override
    public Phase getPhase() { return Phase.ASSEMBLY; }

    public String getName() {
        return "systemPromptAssemblyStage";
    }

    @Override
    public void execute(ConversationContext ctx) {
        String languageClause = buildLanguageClause(ctx.getLanguage());
        String memoryText = (String) ctx.getAgentState().getOrDefault(ConversationContext.KEY_FORMATTED_MEMORY, "");
        String searchText = (String) ctx.getAgentState().getOrDefault(ConversationContext.KEY_FORMATTED_SEARCH, "");

        Map<String, String> params = new HashMap<>();
        params.put("language_clause", languageClause);
        params.put("long_term_memory", memoryText);
        params.put("search_context", searchText);

        String systemPrompt;
        try {
            systemPrompt = templateService.renderTemplate("default-system-prompt", params);
        } catch (IllegalArgumentException e) {
            log.warn("Template not found, using fallback: {}", e.getMessage());
            systemPrompt = FALLBACK_TEMPLATE
                    .replace("{language_clause}", languageClause)
                    .replace("{long_term_memory}", memoryText)
                    .replace("{search_context}", searchText);
        }

        if (systemPrompt == null || systemPrompt.isBlank()) {
            systemPrompt = "你是一个智能助手。请根据上下文回答问题。";
        }

        ctx.getAgentState().put(ConversationContext.KEY_SYSTEM_MESSAGE, SystemMessage.from(systemPrompt));
    }

    private String buildLanguageClause(String language) {
        if (language == null || language.isBlank()) {
            return "";
        }
        String languageName = LANGUAGE_NAMES.getOrDefault(language, language);
        return "请使用 " + languageName + " 回复。";
    }

    @Override
    public int getOrder() {
        return 410;
    }
}
