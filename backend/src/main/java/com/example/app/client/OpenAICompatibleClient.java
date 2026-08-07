package com.example.app.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Component
@RequiredArgsConstructor
@Slf4j
public class OpenAICompatibleClient {

    public static final double SYNC_CHAT_TEMPERATURE = 0.3;
    public static final double STREAM_CHAT_TEMPERATURE = 0.7;
    public static final int DEFAULT_CHAT_MAX_TOKENS = 4096;

    private final ObjectMapper objectMapper;

    public boolean isImageModel(String modelId) {
        return modelId.toLowerCase().contains("dall-e") ||
                modelId.toLowerCase().contains("image") ||
                modelId.toLowerCase().contains("sdxl") ||
                modelId.toLowerCase().contains("stable-diffusion");
    }

    public boolean isStableDiffusionModel(String modelId) {
        String lower = modelId.toLowerCase();
        return lower.contains("stable-diffusion") ||
                lower.contains("sdxl") ||
                lower.contains("sd-");
    }

    private String buildFullUrl(String baseUrl, String endpoint) {
        // 如果 baseUrl 已经包含了完整的端点路径（如 /v1/chat/completions），直接返回；否则拼接
        String normalizedBaseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;

        // 检查是否已经包含了类似的端点路径
        if (normalizedBaseUrl.contains("/v1/chat/completions") ||
                normalizedBaseUrl.contains("/v1/images/generations")) {
            return baseUrl;
        }

        // 否则拼接端点
        return normalizedBaseUrl + endpoint;
    }

    public void generateImage(
            String modelId,
            String baseUrl,
            String apiKey,
            String prompt,
            List<String> imageUrls,
            SseEmitter emitter,
            Consumer<String> onComplete) {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();

        try {
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", modelId);
            requestBody.put("prompt", prompt);
            requestBody.put("n", 1);
            requestBody.put("response_format", "url");

            boolean hasReferenceImage = imageUrls != null && !imageUrls.isEmpty();
            if (hasReferenceImage) {
                String imageDataUri = fetchImageAsBase64(imageUrls.get(0));
                if (imageDataUri != null) {
                    String rawBase64 = imageDataUri.contains(",")
                            ? imageDataUri.substring(imageDataUri.indexOf(",") + 1)
                            : imageDataUri;
                    requestBody.put("image", rawBase64);
                    log.info("Img2img mode: using reference image ({} chars base64)", rawBase64.length());
                }
            }

            String requestBodyStr = objectMapper.writeValueAsString(requestBody);
            RequestBody body = RequestBody.create(
                    requestBodyStr,
                    MediaType.parse("application/json"));

            String fullUrl = buildFullUrl(baseUrl, "/v1/images/generations");

            Request request = new Request.Builder()
                    .url(fullUrl)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .post(body)
                    .build();

            Call call = client.newCall(request);
            emitter.onCompletion(() -> call.cancel());

            call.enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    try {
                        emitter.completeWithError(e);
                    } catch (Exception ex) {
                        log.error("Failed to send error to client", ex);
                    }
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (!response.isSuccessful()) {
                        String errorBody = response.body() != null ? response.body().string() : "No response body";
                        log.error("Image generation failed with code {}: {}", response.code(), errorBody);
                        try {
                            emitter.completeWithError(new RuntimeException(
                                    "Image generation failed: " + response.code() + ". Details: " + errorBody));
                        } catch (Exception ex) {
                            log.error("Failed to send error to client", ex);
                        }
                        return;
                    }

                    try (ResponseBody responseBody = response.body()) {
                        if (responseBody == null) {
                            emitter.complete();
                            return;
                        }

                        String responseBodyStr = responseBody.string();

                        JsonNode node = objectMapper.readTree(responseBodyStr);
                        JsonNode data = node.get("data");
                        if (data != null && data.isArray() && data.size() > 0) {
                            JsonNode firstItem = data.get(0);
                            String imageUrl = null;

                            if (firstItem.has("url")) {
                                imageUrl = firstItem.get("url").asText();
                            } else if (firstItem.has("b64_json")) {
                                imageUrl = "data:image/png;base64," + firstItem.get("b64_json").asText();
                            }

                            if (imageUrl != null) {
                                String markdownImage = "![Generated Image](" + imageUrl + ")";
                                emitter.send(SseEmitter.event()
                                        .name("message")
                                        .data("{\"content\": \"" + escapeJson(markdownImage) + "\"}"));
                                if (onComplete != null) {
                                    onComplete.accept(markdownImage);
                                }
                            }
                        }

                        emitter.complete();
                    } catch (Exception e) {
                        log.error("Failed to process image generation response", e);
                        try {
                            emitter.completeWithError(e);
                        } catch (Exception ex) {
                            log.error("Failed to send error to client", ex);
                        }
                    }
                }
            });

        } catch (Exception e) {
            log.error("Failed to start image generation", e);
            try {
                emitter.completeWithError(e);
            } catch (Exception ex) {
                log.error("Failed to send error to client", ex);
            }
        }
    }

    public void generateImageSdWebui(
            String modelId,
            String baseUrl,
            String apiKey,
            String prompt,
            List<String> imageUrls,
            SseEmitter emitter,
            Consumer<String> onComplete) {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();

        try {
            boolean hasReferenceImage = imageUrls != null && !imageUrls.isEmpty();

            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("prompt", prompt);
            requestBody.put("negative_prompt", "");
            requestBody.put("steps", 20);
            requestBody.put("cfg_scale", 7);

            String normalizedBaseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
            String endpoint;
            if (hasReferenceImage) {
                String imageDataUri = fetchImageAsBase64(imageUrls.get(0));
                if (imageDataUri != null) {
                    String rawBase64 = imageDataUri.contains(",")
                            ? imageDataUri.substring(imageDataUri.indexOf(",") + 1)
                            : imageDataUri;
                    ArrayNode initImages = objectMapper.createArrayNode();
                    initImages.add(rawBase64);
                    requestBody.set("init_images", initImages);
                    requestBody.put("denoising_strength", 0.75);
                    log.info("SD img2img mode: using reference image ({} chars base64)", rawBase64.length());
                }
                endpoint = "sdapi/v1/img2img";
            } else {
                endpoint = "sdapi/v1/txt2img";
            }

            String fullUrl = normalizedBaseUrl + endpoint;

            String requestBodyStr = objectMapper.writeValueAsString(requestBody);
            RequestBody body = RequestBody.create(requestBodyStr, MediaType.parse("application/json"));

            Request request = new Request.Builder()
                    .url(fullUrl)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .post(body)
                    .build();

            Call call = client.newCall(request);
            emitter.onCompletion(() -> call.cancel());

            call.enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    try {
                        emitter.completeWithError(e);
                    } catch (Exception ex) {
                        log.error("Failed to send error to client", ex);
                    }
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (!response.isSuccessful()) {
                        String errorBody = response.body() != null ? response.body().string() : "No response body";
                        log.error("SD WebUI request failed with code {}: {}", response.code(), errorBody);
                        try {
                            emitter.completeWithError(new RuntimeException(
                                    "SD WebUI request failed: " + response.code() + ". " + errorBody));
                        } catch (Exception ex) {
                            log.error("Failed to send error to client", ex);
                        }
                        return;
                    }

                    try (ResponseBody responseBody = response.body()) {
                        if (responseBody == null) {
                            emitter.complete();
                            return;
                        }

                        String responseBodyStr = responseBody.string();
                        JsonNode node = objectMapper.readTree(responseBodyStr);
                        JsonNode images = node.get("images");
                        if (images != null && images.isArray() && images.size() > 0) {
                            String base64Image = images.get(0).asText();
                            String imageUrl = "data:image/png;base64," + base64Image;
                            String markdownImage = "![Generated Image](" + imageUrl + ")";
                            emitter.send(SseEmitter.event()
                                    .name("message")
                                    .data("{\"content\": \"" + escapeJson(markdownImage) + "\"}"));
                            if (onComplete != null) {
                                onComplete.accept(markdownImage);
                            }
                        }

                        emitter.complete();
                    } catch (Exception e) {
                        log.error("Failed to process SD WebUI response", e);
                        try {
                            emitter.completeWithError(e);
                        } catch (Exception ex) {
                            log.error("Failed to send error to client", ex);
                        }
                    }
                }
            });

        } catch (Exception e) {
            log.error("Failed to start SD WebUI image generation", e);
            try {
                emitter.completeWithError(e);
            } catch (Exception ex) {
                log.error("Failed to send error to client", ex);
            }
        }
    }

    /**
     * 将 ChatMessage 列表转换为 OpenAI messages JSON 数组
     */
    private ArrayNode buildMessagesArray(List<ChatMessage> messages) {
        ArrayNode messagesArray = objectMapper.createArrayNode();
        for (ChatMessage msg : messages) {
            ObjectNode msgNode = objectMapper.createObjectNode();
            if (msg instanceof SystemMessage) {
                msgNode.put("role", "system");
            } else if (msg instanceof UserMessage) {
                msgNode.put("role", "user");
            } else if (msg instanceof AiMessage) {
                msgNode.put("role", "assistant");
            } else {
                continue;
            }
            msgNode.put("content", msg.text());
            messagesArray.add(msgNode);
        }
        return messagesArray;
    }

    /**
     * 同步调用 OpenAI 兼容 API（支持完整对话历史）
     */
    public String chatCompletion(
            String modelId,
            String baseUrl,
            String apiKey,
            List<ChatMessage> messages) {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(2, TimeUnit.MINUTES)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();

        try {
            ArrayNode messagesArray = buildMessagesArray(messages);

            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", modelId);
            requestBody.set("messages", messagesArray);
            requestBody.put("stream", false);
            requestBody.put("max_tokens", DEFAULT_CHAT_MAX_TOKENS);
            requestBody.put("temperature", SYNC_CHAT_TEMPERATURE);

            String requestBodyStr = objectMapper.writeValueAsString(requestBody);
            RequestBody body = RequestBody.create(
                    requestBodyStr,
                    MediaType.parse("application/json"));

            String fullUrl = buildFullUrl(baseUrl, "/v1/chat/completions");

            Request request = new Request.Builder()
                    .url(fullUrl)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .post(body)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "No response body";
                    log.error("API request failed with code {}: {}", response.code(), errorBody);
                    throw new RuntimeException("API请求失败: " + response.code() + ". " + errorBody);
                }

                ResponseBody responseBody = response.body();
                if (responseBody == null) {
                    throw new RuntimeException("API返回空响应");
                }

                String responseBodyStr = responseBody.string();
                JsonNode node = objectMapper.readTree(responseBodyStr);
                JsonNode choices = node.get("choices");
                if (choices != null && choices.isArray() && choices.size() > 0) {
                    JsonNode message = choices.get(0).get("message");
                    if (message != null && message.has("content")) {
                        return message.get("content").asText();
                    }
                }

                log.error("Failed to parse response: {}", responseBodyStr);
                throw new RuntimeException("无法解析API响应");
            }

        } catch (IOException e) {
            log.error("Synchronous chat completion failed", e);
            throw new RuntimeException("API调用失败: " + e.getMessage(), e);
        }
    }

    /**
     * 同步多模态对话：把图片转成 data URI 后按 OpenAI 兼容 content 数组发送。
     */
    public String chatCompletionWithImages(
            String modelId,
            String baseUrl,
            String apiKey,
            String systemPrompt,
            String userContent,
            List<String> imageUrls) throws IOException {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(3, TimeUnit.MINUTES)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();

        ArrayNode messagesArray = objectMapper.createArrayNode();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            ObjectNode systemNode = objectMapper.createObjectNode();
            systemNode.put("role", "system");
            systemNode.put("content", systemPrompt);
            messagesArray.add(systemNode);
        }

        ObjectNode userNode = objectMapper.createObjectNode();
        userNode.put("role", "user");
        ArrayNode content = objectMapper.createArrayNode();
        content.addObject().put("type", "text").put("text", userContent);
        if (imageUrls != null) {
            for (String imageUrl : imageUrls) {
                String dataUri = fetchImageAsBase64(imageUrl);
                if (dataUri == null) {
                    continue;
                }
                ObjectNode imagePart = content.addObject();
                imagePart.put("type", "image_url");
                imagePart.putObject("image_url").put("url", dataUri);
            }
        }
        userNode.set("content", content);
        messagesArray.add(userNode);

        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", modelId);
        requestBody.set("messages", messagesArray);
        requestBody.put("stream", false);
        requestBody.put("max_tokens", DEFAULT_CHAT_MAX_TOKENS);
        requestBody.put("temperature", SYNC_CHAT_TEMPERATURE);

        String requestBodyStr = objectMapper.writeValueAsString(requestBody);
        RequestBody body = RequestBody.create(
                requestBodyStr,
                MediaType.parse("application/json"));
        Request request = new Request.Builder()
                .url(buildFullUrl(baseUrl, "/v1/chat/completions"))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "No response body";
                throw new RuntimeException("API request failed: " + response.code() + ". " + errorBody);
            }
            String responseBodyStr = response.body() != null ? response.body().string() : "";
            JsonNode node = objectMapper.readTree(responseBodyStr);
            JsonNode choices = node.path("choices");
            if (choices.isArray() && choices.size() > 0) {
                return choices.get(0).path("message").path("content").asText("");
            }
            throw new RuntimeException("Failed to parse multimodal response");
        }
    }

    /**
     * 同步文生图，返回可插入 Markdown 的图片 URL。
     */
    public String generateImageSync(
            String modelId,
            String baseUrl,
            String apiKey,
            String prompt,
            List<String> imageUrls) throws IOException {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(3, TimeUnit.MINUTES)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();

        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", modelId);
        requestBody.put("prompt", prompt);
        requestBody.put("n", 1);
        requestBody.put("response_format", "url");

        boolean hasReferenceImage = imageUrls != null && !imageUrls.isEmpty();
        if (hasReferenceImage) {
            String imageDataUri = fetchImageAsBase64(imageUrls.get(0));
            if (imageDataUri != null) {
                String rawBase64 = imageDataUri.contains(",")
                        ? imageDataUri.substring(imageDataUri.indexOf(",") + 1)
                        : imageDataUri;
                requestBody.put("image", rawBase64);
            }
        }

        RequestBody body = RequestBody.create(
                objectMapper.writeValueAsString(requestBody),
                MediaType.parse("application/json"));
        Request request = new Request.Builder()
                .url(buildFullUrl(baseUrl, "/v1/images/generations"))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "No response body";
                throw new RuntimeException("Image generation failed: " + response.code() + ". " + errorBody);
            }
            String responseBodyStr = response.body() != null ? response.body().string() : "";
            JsonNode node = objectMapper.readTree(responseBodyStr);
            JsonNode data = node.path("data");
            if (data.isArray() && data.size() > 0) {
                JsonNode firstItem = data.get(0);
                if (firstItem.has("url")) {
                    return firstItem.get("url").asText();
                }
                if (firstItem.has("b64_json")) {
                    return "data:image/png;base64," + firstItem.get("b64_json").asText();
                }
            }
            throw new RuntimeException("Failed to parse image generation response");
        }
    }

    /**
     * 同步调用 OpenAI 兼容 API（单条消息版本，保留向后兼容）
     */
    public String chatCompletion(
            String modelId,
            String baseUrl,
            String apiKey,
            String systemPrompt,
            String userContent) {
        List<ChatMessage> messages = new java.util.ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(SystemMessage.from(systemPrompt));
        }
        messages.add(UserMessage.from(userContent));
        return chatCompletion(modelId, baseUrl, apiKey, messages);
    }

    public void streamChatCompletion(
            String modelId,
            String baseUrl,
            String apiKey,
            List<ChatMessage> messages,
            List<String> imageUrls,
            SseEmitter emitter,
            Consumer<String> onChunk,
            Runnable onComplete) {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.MINUTES)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();

        try {
            ArrayNode messagesArray = buildMessagesArray(messages);

            if (imageUrls != null && !imageUrls.isEmpty()) {
                for (int i = messagesArray.size() - 1; i >= 0; i--) {
                    JsonNode msg = messagesArray.get(i);
                    if ("user".equals(msg.get("role").asText())) {
                        StringBuilder sb = new StringBuilder(msg.get("content").asText());
                        sb.append("\n\n[用户上传的图片]");
                        for (int j = 0; j < imageUrls.size(); j++) {
                            sb.append("\n- 图片 ").append(j + 1).append(": ").append(imageUrls.get(j));
                        }
                        ((ObjectNode) msg).put("content", sb.toString());
                        break;
                    }
                }
            }

            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", modelId);
            requestBody.set("messages", messagesArray);
            requestBody.put("stream", true);
            requestBody.put("max_tokens", DEFAULT_CHAT_MAX_TOKENS);
            requestBody.put("temperature", STREAM_CHAT_TEMPERATURE);

            String requestBodyStr = objectMapper.writeValueAsString(requestBody);
            RequestBody body = RequestBody.create(
                    requestBodyStr,
                    MediaType.parse("application/json"));

            String fullUrl = buildFullUrl(baseUrl, "/v1/chat/completions");

            Request request = new Request.Builder()
                    .url(fullUrl)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .header("Accept", "text/event-stream")
                    .header("Cache-Control", "no-cache")
                    .header("Connection", "keep-alive")
                    .post(body)
                    .build();

            Call call = client.newCall(request);
            emitter.onCompletion(() -> call.cancel());

            call.enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    try {
                        emitter.completeWithError(e);
                    } catch (Exception ex) {
                        log.error("Failed to send error to client", ex);
                    }
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (!response.isSuccessful()) {
                        String errorBody = response.body() != null ? response.body().string() : "No response body";
                        log.error("API request failed with code {}: {}", response.code(), errorBody);
                        try {
                            emitter.completeWithError(new RuntimeException(
                                    "API request failed: " + response.code() + ". Details: " + errorBody));
                        } catch (Exception ex) {
                            log.error("Failed to send error to client", ex);
                        }
                        return;
                    }

                    try (ResponseBody responseBody = response.body()) {
                        if (responseBody == null) {
                            emitter.complete();
                            return;
                        }

                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        StringBuilder jsonBuffer = new StringBuilder();

                        StringBuilder contentBuilder = new StringBuilder();

                        while ((bytesRead = responseBody.byteStream().read(buffer)) != -1) {
                            String chunk = new String(buffer, 0, bytesRead);
                            String[] lines = chunk.split("\n");

                            boolean isHtmlResponse = false;

                            for (String line : lines) {
                                if (line.trim().isEmpty())
                                    continue;

                                if (line.trim().startsWith("<!DOCTYPE") || line.trim().startsWith("<html")) {
                                    isHtmlResponse = true;
                                    log.error("API returned HTML instead of JSON! Base URL may be incorrect.");
                                    break;
                                }

                                if (line.startsWith("data: ")) {
                                    String data = line.substring(6);
                                    if (data.equals("[DONE]")) {
                                        onComplete.run();
                                        emitter.complete();
                                        return;
                                    }
                                    try {
                                        String content = extractContent(data);
                                        if (content != null && !content.isEmpty()) {
                                            contentBuilder.append(content);
                                            onChunk.accept(content);
                                            emitter.send(SseEmitter.event()
                                                    .name("message")
                                                    .data("{\"content\": \"" + escapeJson(content) + "\"}"));
                                        }
                                    } catch (Exception e) {
                                        log.error("Failed to send SSE event: {}", e.getMessage());
                                        return;
                                    }
                                }
                            }

                            if (isHtmlResponse) {
                                log.error("API returned HTML response. Please check your baseUrl configuration.");
                                emitter.completeWithError(new RuntimeException("API返回了HTML页面，请检查baseUrl配置是否正确。"));
                                return;
                            }
                        }
                        onComplete.run();
                        emitter.complete();
                    } catch (Exception e) {
                        try {
                            emitter.completeWithError(e);
                        } catch (Exception ex) {
                            log.error("Failed to send error to client", ex);
                        }
                    }
                }
            });

        } catch (Exception e) {
            log.error("Failed to start streaming", e);
            try {
                emitter.completeWithError(e);
            } catch (Exception ex) {
                log.error("Failed to send error to client", ex);
            }
        }
    }

    /**
     * 流式调用 OpenAI 兼容 API（单条消息版本，保留向后兼容）
     */
    public void streamChatCompletion(
            String modelId,
            String baseUrl,
            String apiKey,
            String prompt,
            List<String> imageUrls,
            SseEmitter emitter,
            Consumer<String> onChunk,
            Runnable onComplete) {
        List<ChatMessage> messages = new java.util.ArrayList<>();
        messages.add(UserMessage.from(prompt));
        streamChatCompletion(modelId, baseUrl, apiKey, messages, imageUrls, emitter, onChunk, onComplete);
    }

    private String extractContent(String jsonData) {
        try {
            JsonNode node = objectMapper.readTree(jsonData);

            JsonNode choices = node.get("choices");
            if (choices != null && choices.isArray() && choices.size() > 0) {
                JsonNode choice = choices.get(0);
                JsonNode delta = choice.get("delta");

                if (delta != null) {
                    JsonNode content = delta.get("content");
                    if (content != null && !content.isNull()) {
                        return content.asText();
                    }

                    JsonNode toolCalls = delta.get("tool_calls");
                    if (toolCalls != null && toolCalls.isArray() && toolCalls.size() > 0) {
                        return extractImageFromToolCall(toolCalls);
                    }
                }

                JsonNode message = choice.get("message");
                if (message != null) {
                    JsonNode toolCalls = message.get("tool_calls");
                    if (toolCalls != null && toolCalls.isArray() && toolCalls.size() > 0) {
                        return extractImageFromToolCall(toolCalls);
                    }
                }
            }

            JsonNode data = node.get("data");
            if (data != null && data.isArray() && data.size() > 0) {
                return extractImageFromData(data);
            }

        } catch (Exception e) {
            log.debug("Failed to extract content from JSON: {}", e.getMessage());
        }
        return null;
    }

    private String extractImageFromToolCall(JsonNode toolCalls) {
        try {
            for (JsonNode toolCall : toolCalls) {
                JsonNode function = toolCall.get("function");
                if (function != null) {
                    String name = function.get("name").asText();
                    if ("generate_image".equals(name) || "dall-e".equals(name)) {
                        JsonNode args = function.get("arguments");
                        if (args != null) {
                            JsonNode imageUrl = args.get("image_url");
                            if (imageUrl != null && !imageUrl.isNull()) {
                                return "![Generated Image](" + imageUrl.asText() + ")";
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Failed to extract image from tool call: {}", e.getMessage());
        }
        return null;
    }

    private String extractImageFromData(JsonNode data) {
        try {
            JsonNode firstItem = data.get(0);
            if (firstItem != null) {
                JsonNode b64Json = firstItem.get("b64_json");
                if (b64Json != null && !b64Json.isNull()) {
                    return "data:image/png;base64," + b64Json.asText();
                }
                JsonNode url = firstItem.get("url");
                if (url != null && !url.isNull()) {
                    return "![Generated Image](" + url.asText() + ")";
                }
            }
        } catch (Exception e) {
            log.debug("Failed to extract image from data: {}", e.getMessage());
        }
        return null;
    }

    private String escapeJson(String input) {
        if (input == null)
            return "";
        return input.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private boolean isLocalUrl(String url) {
        if (url == null)
            return false;
        return url.startsWith("http://localhost") ||
                url.startsWith("http://127.0.0.1") ||
                url.startsWith("http://0.0.0.0") ||
                url.contains("localhost") ||
                url.contains("127.0.0.1");
    }

    private String convertLocalUrlToBase64(String url) {
        try {
            java.net.URL localUrl = new java.net.URL(url);
            java.net.HttpURLConnection connection = (java.net.HttpURLConnection) localUrl.openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            try (java.io.InputStream is = connection.getInputStream()) {
                byte[] bytes = is.readAllBytes();
                String base64 = Base64.getEncoder().encodeToString(bytes);
                String contentType = connection.getContentType();
                if (contentType == null) {
                    contentType = "image/jpeg";
                }
                return "data:" + contentType + ";base64," + base64;
            }
        } catch (Exception e) {
            log.error("Failed to convert local URL to base64: {}", e.getMessage());
            return url;
        }
    }

    private String fetchImageAsBase64(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank())
            return null;

        if (imageUrl.startsWith("data:"))
            return imageUrl;

        try {
            Path uploadPath = Paths.get("uploads/images");
            String filename = extractFilename(imageUrl);
            if (filename != null) {
                Path filePath = uploadPath.resolve(filename);
                if (Files.exists(filePath)) {
                    byte[] bytes = Files.readAllBytes(filePath);
                    String b64 = Base64.getEncoder().encodeToString(bytes);
                    String mime = guessMimeType(filename);
                    return "data:" + mime + ";base64," + b64;
                }
            }
        } catch (Exception e) {
            log.debug("Direct file read failed for img2img, trying HTTP: {}", e.getMessage());
        }

        if (isLocalUrl(imageUrl))
            return convertLocalUrlToBase64(imageUrl);

        try {
            java.net.URL url = new java.net.URL(imageUrl);
            java.net.HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(30000);

            try (java.io.InputStream is = connection.getInputStream()) {
                byte[] bytes = is.readAllBytes();
                String b64 = Base64.getEncoder().encodeToString(bytes);
                String contentType = connection.getContentType();
                if (contentType == null)
                    contentType = "image/png";
                return "data:" + contentType + ";base64," + b64;
            }
        } catch (Exception e) {
            log.error("Failed to fetch image for img2img: {}", imageUrl, e);
            return null;
        }
    }

    private String extractFilename(String imageUrl) {
        if (imageUrl == null)
            return null;
        String path = imageUrl;
        int queryIndex = path.indexOf('?');
        if (queryIndex > 0)
            path = path.substring(0, queryIndex);
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < path.length() - 1) {
            return path.substring(lastSlash + 1);
        }
        return null;
    }

    private String guessMimeType(String filename) {
        if (filename == null)
            return "image/png";
        String lower = filename.toLowerCase();
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg"))
            return "image/jpeg";
        if (lower.endsWith(".png"))
            return "image/png";
        if (lower.endsWith(".gif"))
            return "image/gif";
        if (lower.endsWith(".webp"))
            return "image/webp";
        if (lower.endsWith(".svg"))
            return "image/svg+xml";
        return "image/png";
    }
}
