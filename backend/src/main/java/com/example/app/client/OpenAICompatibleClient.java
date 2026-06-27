package com.example.app.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Component
@RequiredArgsConstructor
@Slf4j
public class OpenAICompatibleClient {

    private final ObjectMapper objectMapper;

    public boolean isImageModel(String modelId) {
        return modelId.toLowerCase().contains("dall-e") ||
                modelId.toLowerCase().contains("image") ||
                modelId.toLowerCase().contains("sdxl") ||
                modelId.toLowerCase().contains("stable-diffusion");
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

            log.info("=== Starting image generation request ===");
            log.info("Model ID: {}", modelId);
            log.info("URL: {}", fullUrl);
            log.info("Prompt length: {} characters", prompt.length());

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
                        log.info("Image generation response: {}", responseBodyStr);

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

    /**
     * 同步调用 OpenAI 兼容 API，返回完整响应文本
     */
    public String chatCompletion(
            String modelId,
            String baseUrl,
            String apiKey,
            String systemPrompt,
            String userContent) {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(2, TimeUnit.MINUTES)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();

        try {
            ArrayNode messagesArray = objectMapper.createArrayNode();

            if (systemPrompt != null && !systemPrompt.isBlank()) {
                ObjectNode systemMessage = objectMapper.createObjectNode();
                systemMessage.put("role", "system");
                systemMessage.put("content", systemPrompt);
                messagesArray.add(systemMessage);
            }

            ObjectNode userMessage = objectMapper.createObjectNode();
            userMessage.put("role", "user");
            userMessage.put("content", userContent);
            messagesArray.add(userMessage);

            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", modelId);
            requestBody.set("messages", messagesArray);
            requestBody.put("stream", false);
            requestBody.put("max_tokens", 4096);
            requestBody.put("temperature", 0.3);

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

            log.info("=== Starting synchronous OpenAI compatible request ===");
            log.info("Model ID: {}", modelId);
            log.info("Full URL: {}", fullUrl);

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

    public void streamChatCompletion(
            String modelId,
            String baseUrl,
            String apiKey,
            String prompt,
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
            ArrayNode contentArray = objectMapper.createArrayNode();
            contentArray.add(objectMapper.createObjectNode()
                    .put("type", "text")
                    .put("text", prompt));

            if (imageUrls != null && !imageUrls.isEmpty()) {
                for (String imageUrl : imageUrls) {
                    ObjectNode imageNode = objectMapper.createObjectNode();
                    imageNode.put("type", "image_url");
                    ObjectNode urlNode = objectMapper.createObjectNode();

                    String processedUrl = imageUrl;
                    if (isLocalUrl(imageUrl)) {
                        processedUrl = convertLocalUrlToBase64(imageUrl);
                        log.info("Converted local image to base64, length: {}", processedUrl.length());
                    }
                    urlNode.put("url", processedUrl);
                    imageNode.set("image_url", urlNode);
                    contentArray.add(imageNode);
                }
            }

            ObjectNode messageNode = objectMapper.createObjectNode();
            messageNode.put("role", "user");
            messageNode.set("content", contentArray);

            JsonNode messages = objectMapper.createArrayNode().add(messageNode);

            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", modelId);
            requestBody.set("messages", messages);
            requestBody.put("stream", true);
            requestBody.put("max_tokens", 4096);
            requestBody.put("temperature", 0.7);

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

            log.info("=== Starting OpenAI compatible request ===");
            log.info("Model ID: {}", modelId);
            log.info("Base URL: {}", baseUrl);
            log.info("Full URL: {}", fullUrl);
            log.info("API Key: {}", apiKey != null && !apiKey.isEmpty() ? "***" : "null/empty");
            log.info("Prompt length: {} characters", prompt.length());
            log.info("Request body: {}", requestBodyStr);

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
                                    log.info("Received streaming data: {}",
                                            data.length() > 50 ? data.substring(0, 50) + "..." : data);
                                    if (data.equals("[DONE]")) {
                                        log.info("Received [DONE], total content length: {}", contentBuilder.length());
                                        onComplete.run();
                                        emitter.complete();
                                        return;
                                    }
                                    try {
                                        String content = extractContent(data);
                                        log.info("Extracted content: '{}'", content);
                                        if (content != null && !content.isEmpty()) {
                                            contentBuilder.append(content);
                                            onChunk.accept(content);
                                            emitter.send(SseEmitter.event()
                                                    .name("message")
                                                    .data("{\"content\": \"" + escapeJson(content) + "\"}"));
                                            log.info("Sent message event with content length: {}", content.length());
                                        } else if (content == null) {
                                            log.info("Content is null for data: {}",
                                                    data.length() > 100 ? data.substring(0, 100) + "..." : data);
                                        } else {
                                            log.info("Content is empty for data: {}",
                                                    data.length() > 100 ? data.substring(0, 100) + "..." : data);
                                        }
                                    } catch (Exception e) {
                                        log.error("Failed to send SSE event for data '{}': {}", data, e.getMessage());
                                        return;
                                    }
                                } else {
                                    log.info("Non-data line received: {}",
                                            line.length() > 50 ? line.substring(0, 50) + "..." : line);
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
                String base64 = java.util.Base64.getEncoder().encodeToString(bytes);
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
}