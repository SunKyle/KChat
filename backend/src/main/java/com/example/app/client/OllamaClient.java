package com.example.app.client;

import com.example.app.config.OllamaConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
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
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", targetModel);
            requestBody.set("messages", buildMessagesArray(messages));
            requestBody.put("stream", true);

            httpStreamingTemplate.streamChatResponse(
                    ollamaConfig.getBaseUrl() + "/api/chat",
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
            requestBody.set("messages", buildMessagesArrayWithImages(messages, base64Images));
            requestBody.put("stream", true);

            httpStreamingTemplate.streamChatResponse(
                    ollamaConfig.getBaseUrl() + "/api/chat",
                    objectMapper.writeValueAsString(requestBody),
                    callback);

        } catch (Exception e) {
            log.error("Multimodal streaming error: {}", e.getMessage());
            throw new RuntimeException("AI model connection timeout or service unavailable", e);
        }
    }

    /**
     * 同步多模态生成：内部复用流式接口并汇总结果。
     */
    @Retry(name = "ollamaRetry")
    @CircuitBreaker(name = "ollamaCB")
    public String generateWithImages(List<ChatMessage> messages, List<String> imageUrls, String model) {
        StringBuilder fullResponse = new StringBuilder();
        streamGenerateWithImages(messages, imageUrls, fullResponse::append, model);
        return fullResponse.toString();
    }

    /**
     * 将 ChatMessage 列表转换为 /api/chat 的 messages JSON 数组
     * 保留 role 结构（system/user/assistant）
     */
    private ArrayNode buildMessagesArray(List<ChatMessage> messages) {
        ArrayNode messagesArray = objectMapper.createArrayNode();
        for (ChatMessage msg : messages) {
            ObjectNode msgNode = objectMapper.createObjectNode();
            if (msg instanceof SystemMessage) {
                msgNode.put("role", "system");
            } else if (msg instanceof UserMessage) {
                msgNode.put("role", "user");
            } else {
                msgNode.put("role", "assistant");
            }
            msgNode.put("content", msg.text());
            messagesArray.add(msgNode);
        }
        return messagesArray;
    }

    /**
     * 构建带图片的 messages JSON 数组
     * 图片附加到最后一个 user 消息上（Ollama /api/chat 的 images 字段在 message 级别）
     */
    private ArrayNode buildMessagesArrayWithImages(List<ChatMessage> messages, List<String> base64Images) {
        ArrayNode messagesArray = objectMapper.createArrayNode();
        for (int i = 0; i < messages.size(); i++) {
            ChatMessage msg = messages.get(i);
            ObjectNode msgNode = objectMapper.createObjectNode();
            if (msg instanceof SystemMessage) {
                msgNode.put("role", "system");
            } else if (msg instanceof UserMessage) {
                msgNode.put("role", "user");
            } else {
                msgNode.put("role", "assistant");
            }
            msgNode.put("content", msg.text());

            // 将 images 附加到最后一个 user 消息
            boolean isLastUser = msg instanceof UserMessage
                    && i == messages.size() - 1
                    && !base64Images.isEmpty();
            if (isLastUser) {
                ArrayNode imagesArray = objectMapper.createArrayNode();
                base64Images.forEach(imagesArray::add);
                msgNode.set("images", imagesArray);
            }
            messagesArray.add(msgNode);
        }
        return messagesArray;
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
