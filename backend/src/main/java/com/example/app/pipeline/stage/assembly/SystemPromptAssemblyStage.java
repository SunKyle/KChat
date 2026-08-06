package com.example.app.pipeline.stage.assembly;

import com.example.app.config.DefaultSystemPrompt;
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
    public Phase getPhase() { return Phase.ASSEMBLY; }

    public String getName() {
        return "systemPromptAssemblyStage";
    }

    @Override
    public void execute(ConversationContext ctx) {
        String languageClause = buildLanguageClause(ctx.getLanguage());
        String memoryText = (String) ctx.getAgentState().getOrDefault(ConversationContext.KEY_FORMATTED_MEMORY, "");
        String searchText = (String) ctx.getAgentState().getOrDefault(ConversationContext.KEY_FORMATTED_SEARCH, "");
        String userProfileText = (String) ctx.getAgentState().getOrDefault(ConversationContext.KEY_FORMATTED_USER_PROFILE, "");

        Map<String, String> params = new HashMap<>();
        params.put("language_clause", languageClause);
        params.put("long_term_memory", memoryText);
        params.put("search_context", searchText);
        params.put("user_profile", userProfileText);

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
                    .replace("{long_term_memory}", memoryText)
                    .replace("{search_context}", searchText);
            templateVersion = -1;
        }

        if (systemPrompt == null || systemPrompt.isBlank()) {
            systemPrompt = "你是一个智能助手。请根据上下文回答问题。";
        }

        ctx.getAgentState().put(ConversationContext.KEY_PROMPT_TEMPLATE_VERSION, templateVersion);
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
