package com.example.app.client;

import com.example.app.config.OllamaConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.function.Consumer;

@Slf4j
@Component
@RequiredArgsConstructor
public class OllamaClient {

    private final ChatLanguageModel chatLanguageModel;
    private final OllamaConfig ollamaConfig;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final java.util.Map<String, dev.langchain4j.model.ollama.OllamaChatModel> modelCache = new java.util.concurrent.ConcurrentHashMap<>();
    private final HttpStreamingTemplate httpStreamingTemplate;

    @Retry(name = "ollamaRetry")
    @CircuitBreaker(name = "ollamaCB")
    public String generate(List<ChatMessage> messages, String model) {
        String targetModel = (model != null && !model.isBlank()) ? model : ollamaConfig.getDefaultModel();
        try {
            dev.langchain4j.model.ollama.OllamaChatModel modelInstance = modelCache.computeIfAbsent(targetModel,
                    key -> dev.langchain4j.model.ollama.OllamaChatModel.builder()
                            .baseUrl(ollamaConfig.getBaseUrl())
                            .modelName(key)
                            .timeout(Duration.ofMinutes(2))
                            .build());
            Response<AiMessage> response = modelInstance.generate(messages);
            return response.content().text();
        } catch (Exception e) {
            log.error("Ollama generate failed: {}", e.getMessage());
            modelCache.remove(targetModel);
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

        try {
            String prompt = buildPrompt(messages);

            log.info("=== Final Prompt ===");
            log.info("Model: {}", targetModel);
            log.info("Prompt:\n{}", prompt);
            log.info("=== End Prompt ===");

            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", targetModel);
            requestBody.put("prompt", prompt);
            requestBody.put("stream", true);

            httpStreamingTemplate.streamJsonResponse(
                    ollamaConfig.getBaseUrl() + "/api/generate",
                    objectMapper.writeValueAsString(requestBody),
                    callback);

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

    @Retry(name = "ollamaRetry")
    @CircuitBreaker(name = "ollamaCB")
    public void streamGenerateWithImages(List<ChatMessage> messages, List<String> imageUrls,
            Consumer<String> callback, String model) {
        String targetModel = (model != null && !model.isBlank()) ? model : ollamaConfig.getDefaultModel();

        try {
            String prompt = buildPrompt(messages);

            log.info("=== Final Prompt (with images) ===");
            log.info("Model: {}", targetModel);
            log.info("Prompt:\n{}", prompt);
            log.info("Image count: {}", imageUrls != null ? imageUrls.size() : 0);
            log.info("=== End Prompt ===");

            List<String> base64Images = new java.util.ArrayList<>();
            if (imageUrls != null) {
                for (String imageUrl : imageUrls) {
                    try {
                        String base64Image = imageUrlToBase64(imageUrl);
                        if (!base64Image.isEmpty()) {
                            base64Images.add(base64Image);
                        }
                    } catch (Exception e) {
                        log.warn("Failed to convert image: {}", imageUrl);
                    }
                }
            }

            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", targetModel);
            requestBody.put("prompt", prompt);
            requestBody.put("stream", true);

            if (!base64Images.isEmpty()) {
                ArrayNode imagesArray = objectMapper.createArrayNode();
                base64Images.forEach(imagesArray::add);
                requestBody.set("images", imagesArray);
            }

            httpStreamingTemplate.streamJsonResponse(
                    ollamaConfig.getBaseUrl() + "/api/generate",
                    objectMapper.writeValueAsString(requestBody),
                    callback);

        } catch (Exception e) {
            log.error("Multimodal streaming error: {}", e.getMessage());
            throw new RuntimeException("AI model connection timeout or service unavailable", e);
        }
    }

    private String buildPrompt(List<ChatMessage> messages) {
        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder
                .append("""
                        You are a helpful assistant. The following is a conversation history. Please respond to the user's latest question based on the provided context.

                        """);

        for (ChatMessage message : messages) {
            if (message instanceof dev.langchain4j.data.message.SystemMessage) {
                promptBuilder.append("System: ").append(message.text()).append("\n");
            } else if (message instanceof dev.langchain4j.data.message.UserMessage) {
                promptBuilder.append("User: ").append(message.text()).append("\n");
            } else if (message instanceof dev.langchain4j.data.message.AiMessage) {
                promptBuilder.append("Assistant: ").append(message.text()).append("\n");
            } else {
                promptBuilder.append(message.text()).append("\n");
            }
        }
        promptBuilder.append("\nAssistant: ");
        return promptBuilder.toString();
    }

    private String imageUrlToBase64(String imageUrl) throws IOException {
        URL url = new URL(imageUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(10000);

        try (InputStream inputStream = connection.getInputStream()) {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            byte[] imageBytes = outputStream.toByteArray();
            return Base64.getEncoder().encodeToString(imageBytes);
        } finally {
            connection.disconnect();
        }
    }

    @Retry(name = "ollamaRetry")
    @CircuitBreaker(name = "ollamaCB")
    public List<String> listModels() {
        try {
            URL url = new URL(ollamaConfig.getBaseUrl() + "/api/tags");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(10000);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Content-Type", "application/json");

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }

                JsonNode root = objectMapper.readTree(response.toString());
                JsonNode models = root.get("models");
                if (models != null && models.isArray()) {
                    return java.util.stream.StreamSupport.stream(models.spliterator(), false)
                            .map(node -> node.has("name") ? node.get("name").asText() : "")
                            .filter(name -> !name.isEmpty())
                            .toList();
                }
            }
            connection.disconnect();
        } catch (Exception e) {
            log.error("Failed to fetch models: {}", e.getMessage());
        }
        return List.of();
    }

    public void clearModelCache() {
        modelCache.clear();
        log.info("Model cache cleared");
    }

    public void removeFromCache(String modelName) {
        modelCache.remove(modelName);
    }

    public int getCacheSize() {
        return modelCache.size();
    }
}
