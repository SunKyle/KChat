package com.example.app.client;

import com.example.app.config.OllamaConfig;
import com.example.app.config.StreamingConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.output.Response;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

@Slf4j
@Component
@RequiredArgsConstructor
public class OllamaClient {

    private final ChatLanguageModel chatLanguageModel;
    private final OllamaConfig ollamaConfig;
    private final StreamingConfig streamingConfig;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, OllamaChatModel> modelCache = new ConcurrentHashMap<>();

    @Value("${memory.long-term.embedding-model:locusai/all-minilm-l6-v2}")
    private String embeddingModel;

    private volatile List<String> cachedOllamaModels = null;
    private volatile long lastModelsFetchTime = 0L;

    /**
     * 按 modelName 获取 Ollama 同步聊天模型实例（缓存）。
     * 供 AiServiceFactory 在 Ollama 模型路由时使用。
     */
    public ChatLanguageModel chatModel(String modelName) {
        String targetModel = (modelName != null && !modelName.isBlank()) ? modelName : ollamaConfig.getDefaultModel();
        return modelCache.computeIfAbsent(targetModel,
                key -> OllamaChatModel.builder()
                        .baseUrl(ollamaConfig.getBaseUrl())
                        .modelName(key)
                        .timeout(Duration.ofMinutes(ollamaConfig.getTimeoutMinutes()))
                        .build());
    }

    @Retry(name = "ollamaRetry")
    @CircuitBreaker(name = "ollamaCB")
    public String generate(List<ChatMessage> messages, String model) {
        String targetModel = (model != null && !model.isBlank()) ? model : ollamaConfig.getDefaultModel();
        try {
            OllamaChatModel modelInstance = (OllamaChatModel) chatModel(targetModel);
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

    /**
     * 流式生成：使用 LangChain4j 的 OllamaStreamingChatModel，
     * 框架负责 HTTP/SSE/JSON 序列化，onNext 回调驱动业务 callback。
     *
     * 注意：底层 OllamaStreamingChatModel.generate() 是异步的（Retrofit enqueue），
     * 但调用方（如 MultimodalExecutionStage）依赖同步语义（在流结束后 return collected）。
     * 因此用 CountDownLatch 阻塞当前线程，等待 onComplete/onError 释放。
     * 模型内部 timeout 会触发 onError，避免死锁。
     */
    @Retry(name = "ollamaRetry")
    @CircuitBreaker(name = "ollamaCB")
    public void streamGenerate(List<ChatMessage> messages, Consumer<String> callback, String model) {
        String targetModel = (model != null && !model.isBlank()) ? model : ollamaConfig.getDefaultModel();
        StreamingChatLanguageModel streamingModel = streamingConfig.streamingModel(targetModel);
        blockUntilComplete(streamingModel, messages, callback, "Streaming error");
    }

    @Retry(name = "ollamaRetry")
    @CircuitBreaker(name = "ollamaCB")
    public void streamGenerate(List<ChatMessage> messages, Consumer<String> callback) {
        streamGenerate(messages, callback, null);
    }

    /**
     * 多模态流式生成：把 imageUrls 转为 ImageContent 附加到最后一条 UserMessage，
     * 框架自动处理 base64 编码与 Ollama /api/chat 的 images 字段序列化。
     * 保留 per-image 容错：单张图片获取失败不影响其他图片。
     */
    @Retry(name = "ollamaRetry")
    @CircuitBreaker(name = "ollamaCB")
    public void streamGenerateWithImages(List<ChatMessage> messages, List<String> imageUrls,
            Consumer<String> callback, String model) {
        String targetModel = (model != null && !model.isBlank()) ? model : ollamaConfig.getDefaultModel();
        List<ChatMessage> finalMessages = attachImagesToLastUserMessage(messages, imageUrls);
        StreamingChatLanguageModel streamingModel = streamingConfig.streamingModel(targetModel);
        blockUntilComplete(streamingModel, finalMessages, callback, "Multimodal streaming error");
    }

    /**
     * 同步等待流式生成完成：底层 generate() 异步回调，用 latch 阻塞当前线程。
     * latch 超时设为模型 timeout + 60s 缓冲，作为兜底（正常路径靠 onComplete/onError 释放）。
     */
    private void blockUntilComplete(StreamingChatLanguageModel streamingModel, List<ChatMessage> messages,
            Consumer<String> callback, String errorLabel) {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        streamingModel.generate(messages, new StreamingResponseHandler<AiMessage>() {
            @Override
            public void onNext(String partial) {
                if (partial != null && !partial.isEmpty()) {
                    callback.accept(partial);
                }
            }

            @Override
            public void onComplete(Response<AiMessage> response) {
                latch.countDown();
            }

            @Override
            public void onError(Throwable error) {
                log.error("{}: {}", errorLabel, error.getMessage());
                errorRef.set(error);
                latch.countDown();
            }
        });

        long latchTimeoutSeconds = ollamaConfig.getTimeoutMinutes() * 60L + 60L;
        try {
            if (!latch.await(latchTimeoutSeconds, TimeUnit.SECONDS)) {
                throw new RuntimeException("AI model streaming timed out after " + latchTimeoutSeconds + "s");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Streaming interrupted", e);
        }
        Throwable error = errorRef.get();
        if (error != null) {
            throw new RuntimeException("AI model connection timeout or service unavailable", error);
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
     * 将 imageUrls 转换为 ImageContent 并附加到最后一条 UserMessage。
     * 保留 imageUrlToBase64 的 per-image 容错：单张失败跳过，不影响其他图片。
     */
    private List<ChatMessage> attachImagesToLastUserMessage(List<ChatMessage> messages, List<String> imageUrls) {
        if (imageUrls == null || imageUrls.isEmpty()) {
            return messages;
        }

        int lastUserIdx = -1;
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i) instanceof UserMessage) {
                lastUserIdx = i;
                break;
            }
        }
        if (lastUserIdx == -1) {
            return messages;
        }

        List<ImageContent> imageContents = new ArrayList<>();
        if (imageUrls != null) {
            for (String imageUrl : imageUrls) {
                try {
                    String base64Image = imageUrlToBase64(imageUrl);
                    if (!base64Image.isEmpty()) {
                        Image image = Image.builder().base64Data(base64Image).build();
                        imageContents.add(ImageContent.from(image));
                    }
                } catch (Exception e) {
                    log.warn("Failed to convert image: {}", imageUrl);
                }
            }
        }
        if (imageContents.isEmpty()) {
            return messages;
        }

        UserMessage original = (UserMessage) messages.get(lastUserIdx);
        ImageContent[] imageArray = imageContents.toArray(new ImageContent[0]);
        UserMessage withImages = UserMessage.from(original.text(), imageArray);

        List<ChatMessage> result = new ArrayList<>(messages);
        result.set(lastUserIdx, withImages);
        return result;
    }

    private String imageUrlToBase64(String imageUrl) throws IOException {
        URL url = new URL(imageUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(ollamaConfig.getImageFetchConnectTimeoutMs());
        connection.setReadTimeout(ollamaConfig.getImageFetchReadTimeoutMs());

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
        long now = System.currentTimeMillis();
        if (cachedOllamaModels != null && now - lastModelsFetchTime < ollamaConfig.getModelsCacheTtlMs()) {
            return cachedOllamaModels;
        }

        List<String> models = fetchOllamaModels();
        if (!models.isEmpty()) {
            cachedOllamaModels = models;
            lastModelsFetchTime = now;
            return models;
        }
        if (cachedOllamaModels != null) {
            lastModelsFetchTime = now;
            return cachedOllamaModels;
        }
        return List.of();
    }

    /**
     * 调用 Ollama embedding 接口，返回归一化前的向量。
     * 优先使用新版 /api/embed，失败时回退 /api/embeddings。
     */
    public float[] embed(String text) {
        if (text == null || text.isBlank()) {
            return new float[0];
        }
        try {
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", embeddingModel);
            requestBody.put("input", text);
            String response = postJson("/api/embed", requestBody.toString());
            JsonNode root = objectMapper.readTree(response);
            JsonNode embeddings = root.path("embeddings");
            if (embeddings.isArray() && embeddings.size() > 0) {
                return toFloatArray(embeddings.get(0));
            }
        } catch (Exception e) {
            log.warn("Ollama /api/embed failed, trying /api/embeddings: {}", e.getMessage());
        }

        try {
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", embeddingModel);
            requestBody.put("prompt", text);
            String response = postJson("/api/embeddings", requestBody.toString());
            JsonNode root = objectMapper.readTree(response);
            JsonNode embedding = root.path("embedding");
            if (embedding.isArray() && embedding.size() > 0) {
                return toFloatArray(embedding);
            }
        } catch (Exception e) {
            log.warn("Ollama /api/embeddings failed: {}", e.getMessage());
        }
        return new float[0];
    }

    private String postJson(String endpoint, String body) throws Exception {
        URL url = new URL(ollamaConfig.getBaseUrl() + endpoint);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(ollamaConfig.getEmbedConnectTimeoutMs());
        connection.setReadTimeout(ollamaConfig.getEmbedReadTimeoutMs());
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);
        try (java.io.OutputStream os = connection.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            return response.toString();
        } finally {
            connection.disconnect();
        }
    }

    private float[] toFloatArray(JsonNode node) {
        float[] result = new float[node.size()];
        for (int i = 0; i < node.size(); i++) {
            result[i] = node.get(i).floatValue();
        }
        return result;
    }

    private List<String> fetchOllamaModels() {
        try {
            URL url = new URL(ollamaConfig.getBaseUrl() + "/api/tags");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(ollamaConfig.getListModelsConnectTimeoutMs());
            connection.setReadTimeout(ollamaConfig.getListModelsReadTimeoutMs());
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
            log.warn("Failed to fetch Ollama models: {}", e.getMessage());
        }
        return List.of();
    }

    public void clearModelCache() {
        modelCache.clear();
        streamingConfig.evictAll();
        cachedOllamaModels = null;
        lastModelsFetchTime = 0L;
        log.info("Model cache cleared");
    }

    public void removeFromCache(String modelName) {
        modelCache.remove(modelName);
        streamingConfig.evict(modelName);
    }

    public int getCacheSize() {
        return modelCache.size();
    }
}
