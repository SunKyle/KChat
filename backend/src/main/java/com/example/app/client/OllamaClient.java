package com.example.app.client;

import com.example.app.config.OllamaConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;

@Slf4j
@Component
@RequiredArgsConstructor
public class OllamaClient {

    private final ChatLanguageModel chatLanguageModel;
    private final OllamaConfig ollamaConfig;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String generate(List<ChatMessage> messages) {
        log.debug("Generating response using model: {}", ollamaConfig.getDefaultModel());
        Response<AiMessage> response = chatLanguageModel.generate(messages);
        return response.content().text();
    }

    public void streamGenerate(List<ChatMessage> messages, Consumer<String> callback) {
        log.debug("Streaming response using model: {}", ollamaConfig.getDefaultModel());

        try {
            URL url = new URL(ollamaConfig.getBaseUrl() + "/api/generate");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "application/json");
            connection.setDoOutput(true);
            connection.setChunkedStreamingMode(0);

            StringBuilder promptBuilder = new StringBuilder();
            for (ChatMessage message : messages) {
                promptBuilder.append(message.text()).append("\n");
            }

            String jsonInput = "{\"model\": \"" + ollamaConfig.getDefaultModel() + "\", \"prompt\": \""
                    + escapeJson(promptBuilder.toString()) + "\", \"stream\": true}";

            connection.getOutputStream().write(jsonInput.getBytes(StandardCharsets.UTF_8));

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    try {
                        JsonNode node = objectMapper.readTree(line);
                        String response = node.has("response") ? node.get("response").asText() : "";
                        if (!response.isEmpty()) {
                            callback.accept(response);
                        }
                    } catch (Exception e) {
                        log.warn("Failed to parse streaming response: {}", line);
                    }
                }
            }

            connection.disconnect();

        } catch (Exception e) {
            log.error("Streaming error", e);
        }
    }

    private String escapeJson(String input) {
        return input.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
