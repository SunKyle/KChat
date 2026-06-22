package com.example.app.service;

import com.example.app.client.OllamaClient;
import com.example.app.client.OpenAICompatibleClient;
import com.example.app.entity.ModelConfig;
import dev.langchain4j.data.message.ChatMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class TitleGenerationService {

    private final OllamaClient ollamaClient;
    private final OpenAICompatibleClient openAICompatibleClient;
    private final ModelConfigService modelConfigService;

    public String generateTitle(String userMessage, String aiResponse, String model) {
        String prompt = buildTitlePrompt(userMessage, aiResponse);
        String raw;

        ModelConfig customConfig = modelConfigService.getConfigByModelId(model);
        if (customConfig != null) {
            String actualModelId = model.substring(customConfig.getName().length() + 1);
            raw = openAICompatibleClient.chatCompletion(
                    actualModelId, customConfig.getBaseUrl(), customConfig.getApiKey(),
                    null, prompt);
        } else {
            List<ChatMessage> messages = List.of(
                    dev.langchain4j.data.message.UserMessage.from(prompt));
            raw = ollamaClient.generate(messages, model);
        }

        return cleanTitle(raw);
    }

    private String cleanTitle(String raw) {
        if (raw == null || raw.isBlank()) return "";
        String title = raw.trim()
                .replaceAll("^[\"'\"《》\\[\\]\\s]+|[\"'\"《》\\[\\]\\s]+$", "")
                .replaceAll("^标题[：:]\\s*", "")
                .replaceAll("[\n\r]", " ");
        if (title.length() > 50) {
            title = title.substring(0, 50);
        }
        return title;
    }

    private String buildTitlePrompt(String userMessage, String aiResponse) {
        String truncatedUser = userMessage.length() > 200 ? userMessage.substring(0, 200) : userMessage;
        String truncatedAi = aiResponse.length() > 200 ? aiResponse.substring(0, 200) : aiResponse;
        return String.format(
                "根据以下对话内容，生成一个简短的标题（3-15个字）。直接输出标题，不要加引号、编号或其他修饰。\n\n用户：%s\nAI：%s",
                truncatedUser, truncatedAi);
    }
}
