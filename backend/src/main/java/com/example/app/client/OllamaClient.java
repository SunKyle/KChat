package com.example.app.client;

import com.example.app.config.OllamaConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
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

    @Retry(name = "ollamaRetry")
    @CircuitBreaker(name = "ollamaCB")
    public String generate(List<ChatMessage> messages, String model) {
        String targetModel = (model != null && !model.isBlank()) ? model : ollamaConfig.getDefaultModel();
        log.debug("Generating response using model: {}", targetModel);
        try {
            dev.langchain4j.model.ollama.OllamaChatModel modelInstance = dev.langchain4j.model.ollama.OllamaChatModel
                    .builder()
                    .baseUrl(ollamaConfig.getBaseUrl())
                    .modelName(targetModel)
                    .build();
            Response<AiMessage> response = modelInstance.generate(messages);
            return response.content().text();
        } catch (Exception e) {
            log.error("Ollama generate failed: {}", e.getMessage());
            throw e;
        }
    }

    @Retry(name = "ollamaRetry")
    @CircuitBreaker(name = "ollamaCB")
    public String generate(List<ChatMessage> messages) {
        return generate(messages, null);
    }

    @Retry(name = "ollamaRetry")
    @CircuitBreaker(name = "ollamaCB")
    public void streamGenerate(List<ChatMessage> messages, Consumer<String> callback, String model) {
        String targetModel = (model != null && !model.isBlank()) ? model : ollamaConfig.getDefaultModel();
        log.debug("Streaming response using model: {}", targetModel);

        try {
            URL url = new URL(ollamaConfig.getBaseUrl() + "/api/generate");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(30000);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "application/json");
            connection.setDoOutput(true);
            connection.setChunkedStreamingMode(0);

            StringBuilder promptBuilder = new StringBuilder();
            for (ChatMessage message : messages) {
                promptBuilder.append(message.text()).append("\n");
            }

            String jsonInput = "{\"model\": \"" + targetModel + "\", \"prompt\": \""
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
            log.error("Streaming error: {}", e.getMessage());
            throw new RuntimeException("AI model connection timeout or service unavailable", e);
        }
    }

    @Retry(name = "ollamaRetry")
    @CircuitBreaker(name = "ollamaCB")
    public void streamGenerate(List<ChatMessage> messages, Consumer<String> callback) {
        streamGenerate(messages, callback, null);
    }

    private String escapeJson(String input) {
        return input.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
