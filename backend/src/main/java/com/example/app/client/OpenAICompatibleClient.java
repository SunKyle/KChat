package com.example.app.client;

import com.example.app.config.OpenAIClientProperties;
import com.example.app.config.OpenAIClientProperties.Timeout;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Component
@RequiredArgsConstructor
@Slf4j
public class OpenAICompatibleClient {

    private final ObjectMapper objectMapper;
    private final OpenAIClientProperties props;
    private final OpenAiModelFactory modelFactory;

    @Value("${app.image.upload-dir:uploads/images}")
    private String uploadDir;

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
        String normalizedBaseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        if (normalizedBaseUrl.contains("/v1/chat/completions") ||
                normalizedBaseUrl.contains("/v1/images/generations")) {
            return baseUrl;
        }
        return normalizedBaseUrl + endpoint;
    }

    private OkHttpClient buildClient(Timeout t) {
        return new OkHttpClient.Builder()
                .connectTimeout(t.getConnectSeconds(), TimeUnit.SECONDS)
                .readTimeout(t.getReadSeconds(), TimeUnit.SECONDS)
                .writeTimeout(t.getWriteSeconds(), TimeUnit.SECONDS)
                // 自动重试连接失败（如 stream was reset: CANCEL），增强网络稳定性
                .retryOnConnectionFailure(true)
                .build();
    }

    public void generateImage(
            String modelId,
            String baseUrl,
            String apiKey,
            String prompt,
            List<String> imageUrls,
            SseEmitter emitter,
            Consumer<String> onComplete) {
        OkHttpClient client = buildClient(props.getImageGen());

        try {
            boolean hasReferenceImage = imageUrls != null && !imageUrls.isEmpty();
            Request request;
            if (hasReferenceImage) {
                // 图生图必须走 /v1/images/edits 的 multipart/form-data 接口，
                // 不能把 image 塞进 /v1/images/generations 的 JSON body（中转层会拒绝）。
                String referenceImageUrl = imageUrls.get(0);
                MultipartBody multipart = new MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("model", modelId)
                        .addFormDataPart("prompt", prompt)
                        .addFormDataPart("image", referenceImageUrl)
                        .addFormDataPart("n", "1")
                        .addFormDataPart("response_format", "url")
                        .build();
                request = new Request.Builder()
                        .url(buildFullUrl(baseUrl, "/v1/images/edits"))
                        .header("Authorization", "Bearer " + apiKey)
                        .post(multipart)
                        .build();
                log.info("Img2img mode: editing via multipart images/edits with reference image");
            } else {
                ObjectNode requestBody = objectMapper.createObjectNode();
                requestBody.put("model", modelId);
                requestBody.put("prompt", prompt);
                requestBody.put("n", 1);
                requestBody.put("response_format", "url");

                String requestBodyStr = objectMapper.writeValueAsString(requestBody);
                RequestBody body = RequestBody.create(
                        requestBodyStr,
                        MediaType.parse("application/json"));

                request = new Request.Builder()
                        .url(buildFullUrl(baseUrl, "/v1/images/generations"))
                        .header("Authorization", "Bearer " + apiKey)
                        .header("Content-Type", "application/json")
                        .post(body)
                        .build();
            }

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
                public void onResponse(Call call, okhttp3.Response response) throws IOException {
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

                    try (okhttp3.ResponseBody responseBody = response.body()) {
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
        OkHttpClient client = buildClient(props.getSdWebui());

        try {
            boolean hasReferenceImage = imageUrls != null && !imageUrls.isEmpty();

            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("prompt", prompt);
            requestBody.put("negative_prompt", "");
            requestBody.put("steps", props.getSd().getSteps());
            requestBody.put("cfg_scale", props.getSd().getCfgScale());

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
                    requestBody.put("denoising_strength", props.getSd().getDenoisingStrength());
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
                public void onResponse(Call call, okhttp3.Response response) throws IOException {
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

                    try (okhttp3.ResponseBody responseBody = response.body()) {
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
     * 同步调用 OpenAI 兼容 API（支持完整对话历史）
     * 改用 LangChain4j OpenAiChatModel.chat()，由框架处理 HTTP/JSON。
     */
    public String chatCompletion(
            String modelId,
            String baseUrl,
            String apiKey,
            List<ChatMessage> messages) {
        ChatModel model = modelFactory.chatModel(baseUrl, apiKey, modelId);
        ChatResponse response = model.chat(messages);
        return response.aiMessage().text();
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
        List<ChatMessage> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(SystemMessage.from(systemPrompt));
        }
        messages.add(UserMessage.from(userContent));
        return chatCompletionWithImages(modelId, baseUrl, apiKey, messages, imageUrls);
    }

    /**
     * 同步多模态对话（完整历史）：保留 system/assistant/user 历史，
     * 并把图片以 ImageContent 形式附加到最后一条 user 消息，框架自动序列化 OpenAI content 数组。
     */
    public String chatCompletionWithImages(
            String modelId,
            String baseUrl,
            String apiKey,
            List<ChatMessage> messages,
            List<String> imageUrls) throws IOException {
        List<ChatMessage> finalMessages = attachImagesToLastUserMessage(messages, imageUrls);
        ChatModel model = modelFactory.chatModel(baseUrl, apiKey, modelId);
        ChatResponse response = model.chat(finalMessages);
        return response.aiMessage().text();
    }

    /**
     * 同步文生图，返回可插入 Markdown 的图片 URL。
     * 图像生成不在 LangChain4j 范畴，保留手写 OkHttp 实现。
     */
    public String generateImageSync(
            String modelId,
            String baseUrl,
            String apiKey,
            String prompt,
            List<String> imageUrls) throws IOException {
        OkHttpClient client = buildClient(props.getMultimodal());

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

        IOException lastException = null;
        int maxRetries = 2; // 额外重试次数
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            if (attempt > 0) {
                log.warn("[generateImageSync] Retry attempt {}/{} after connection failure", attempt, maxRetries);
                try {
                    Thread.sleep(1000L * attempt); // 简单退避
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Retry interrupted", ie);
                }
            }
            try (okhttp3.Response response = client.newCall(request).execute()) {
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
            } catch (IOException e) {
                lastException = e;
                if (!isRetryable(e) || attempt == maxRetries) {
                    throw e;
                }
            }
        }
        throw lastException != null ? lastException : new IOException("Unknown error during image generation");
    }

    /**
     * 同步调用 OpenAI 兼容图像编辑接口（图生图 / img2img）。
     *
     * <p>OpenAI 规范（以及 new-api / one-api 等中转层）要求 images/edits 使用
     * {@code multipart/form-data} 而非 JSON，否则中转层会拒绝（如 "Unknown parameter: 'image'"）。
     * 参考图通过 multipart 的 {@code image} 字段以 HTTP URL 形式传入。
     *
     * @throws IOException 网络层失败
     */
    public String generateImageEditSync(
            String modelId,
            String baseUrl,
            String apiKey,
            String prompt,
            String referenceImageUrl) throws IOException {
        OkHttpClient client = buildClient(props.getMultimodal());

        MultipartBody body = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("model", modelId)
                .addFormDataPart("prompt", prompt)
                .addFormDataPart("image", referenceImageUrl)
                .build();

        Request request = new Request.Builder()
                .url(buildFullUrl(baseUrl, "/v1/images/edits"))
                .header("Authorization", "Bearer " + apiKey)
                .post(body)
                .build();

        try (okhttp3.Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "No response body";
                throw new RuntimeException("Image edit failed: " + response.code() + ". " + errorBody);
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
            throw new RuntimeException("Failed to parse image edit response");
        }
    }

    /**
     * 判断 IOException 是否为可重试的连接/流错误
     */
    private boolean isRetryable(IOException e) {
        String msg = e.getMessage();
        if (msg == null)
            return false;
        return msg.contains("stream was reset") ||
                msg.contains("Connection reset") ||
                msg.contains("SocketTimeoutException") ||
                msg.contains("Broken pipe");
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
        List<ChatMessage> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(SystemMessage.from(systemPrompt));
        }
        messages.add(UserMessage.from(userContent));
        return chatCompletion(modelId, baseUrl, apiKey, messages);
    }

    /**
     * 流式调用 OpenAI 兼容 API（完整消息历史版本）
     *
     * 改用 LangChain4j OpenAiStreamingChatModel.chat()，框架处理 SSE 解析与 chunk 边界。
     * 框架为异步回调（OkHttp enqueue），与原 call.enqueue() 行为一致：
     * 方法立即返回，onPartialResponse/onCompleteResponse/onError 在框架线程驱动 SseEmitter。
     *
     * 保留 tool_calls 中的图像生成工具调用识别（generate_image / dall-e）：
     * 框架在 onComplete 交付 AiMessage.toolExecutionRequests()，从中提取 image_url 转
     * Markdown。
     */
    public void streamChatCompletion(
            String modelId,
            String baseUrl,
            String apiKey,
            List<ChatMessage> messages,
            List<String> imageUrls,
            SseEmitter emitter,
            Consumer<String> onChunk,
            Runnable onComplete) {
        // 标记是否已通过 onPartialResponse 投递过流式内容，用于 onError 区分"流末尾回收噪声"与"真实错误"
        java.util.concurrent.atomic.AtomicBoolean receivedContent = new java.util.concurrent.atomic.AtomicBoolean(
                false);
        // 标记业务 onComplete 回调是否已执行，防止重复执行
        java.util.concurrent.atomic.AtomicBoolean completed = new java.util.concurrent.atomic.AtomicBoolean(
                false);
        try {
            List<ChatMessage> finalMessages = attachImagesToLastUserMessage(messages, imageUrls);
            StreamingChatModel model = modelFactory.streamingModel(baseUrl, apiKey, modelId);
            ChatRequest chatRequest = ChatRequest.builder().messages(finalMessages).build();
            model.chat(chatRequest, new StreamingChatResponseHandler() {
                @Override
                public void onPartialResponse(String partialResponse) {
                    if (partialResponse == null || partialResponse.isEmpty()) {
                        return;
                    }
                    receivedContent.set(true);
                    onChunk.accept(partialResponse);
                    try {
                        emitter.send(SseEmitter.event()
                                .name("message")
                                .data("{\"content\": \"" + escapeJson(partialResponse) + "\"}"));
                    } catch (Exception e) {
                        log.error("Failed to send SSE event: {}", e.getMessage());
                    }
                }

                @Override
                public void onCompleteResponse(ChatResponse chatResponse) {
                    // 检查 tool_calls 中的图像生成工具调用，转换为 Markdown 图片输出
                    AiMessage aiMessage = chatResponse.aiMessage();
                    List<ToolExecutionRequest> toolCalls = aiMessage.toolExecutionRequests();
                    if (toolCalls != null && !toolCalls.isEmpty()) {
                        String imageMarkdown = extractImageFromToolCalls(toolCalls);
                        if (imageMarkdown != null) {
                            onChunk.accept(imageMarkdown);
                            try {
                                emitter.send(SseEmitter.event()
                                        .name("message")
                                        .data("{\"content\": \"" + escapeJson(imageMarkdown) + "\"}"));
                            } catch (Exception e) {
                                log.error("Failed to send tool-call image SSE: {}", e.getMessage());
                            }
                        }
                    }
                    if (onComplete != null && completed.compareAndSet(false, true)) {
                        try {
                            onComplete.run();
                        } catch (Exception e) {
                            log.warn("onComplete callback failed: {}", e.getMessage());
                        }
                    }
                    try {
                        emitter.complete();
                    } catch (Exception e) {
                        log.debug("emitter.complete() failed (client likely disconnected): {}", e.getMessage());
                    }
                }

                @Override
                public void onError(Throwable error) {
                    String errorMsg = error.getMessage();
                    // LangChain4j 1.x 在 SSE 流结束后，
                    // 客户端若已断开，Tomcat 回收 ServletResponse，框架访问已关闭对象
                    // 触发 "recycled" 异常。此时内容已通过 onPartialResponse 投递完毕，
                    // 降级为正常结束，避免前端收到误报的流错误。
                    if (errorMsg != null && errorMsg.contains("recycled") && receivedContent.get()) {
                        log.debug("Streaming response recycled after content delivered (ignored): {}", errorMsg);
                        if (onComplete != null && completed.compareAndSet(false, true)) {
                            try {
                                onComplete.run();
                            } catch (Exception e) {
                                log.warn("onComplete callback failed: {}", e.getMessage());
                            }
                        }
                        try {
                            emitter.complete();
                        } catch (Exception e) {
                            log.debug("emitter.complete() failed (client likely disconnected): {}",
                                    e.getMessage());
                        }
                        return;
                    }
                    log.error("Streaming chat completion error: {}", errorMsg, error);
                    try {
                        emitter.completeWithError(error);
                    } catch (Exception ex) {
                        log.debug("Failed to send error to client (client likely disconnected): {}",
                                ex.getMessage());
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
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(UserMessage.from(prompt));
        streamChatCompletion(modelId, baseUrl, apiKey, messages, imageUrls, emitter, onChunk, onComplete);
    }

    /**
     * 把 imageUrls 转换为 ImageContent 并附加到最后一条 UserMessage。
     * 复用 fetchImageAsBase64 的本地文件/localhost/远程 URL 处理逻辑，
     * 框架的 OpenAI 集成会把 Image.base64Data 重新拼装为 data:<mime>;base64,<data> URL。
     *
     * <p>
     * 供 Agent 模式（ModelRoutingStage）复用：Agent 路径走 LangChain4j 原生
     * ChatModel.chat(ChatRequest)，绕过了本类的 chatCompletion/streamChatCompletion，
     * 需要由调用方在构建 ChatRequest 前显式附加图片。
     *
     * @return 新的消息列表（不修改入参），若无可附加的图片则原样返回入参
     */
    public List<ChatMessage> attachImagesToLastUserMessage(List<ChatMessage> messages, List<String> imageUrls) {
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
        for (String imageUrl : imageUrls) {
            String dataUri = fetchImageAsBase64(imageUrl);
            ImageContent ic = toImageContent(dataUri);
            if (ic != null) {
                imageContents.add(ic);
            }
        }
        if (imageContents.isEmpty()) {
            log.warn("No image could be attached for model request: {}", imageUrls);
            return messages;
        }

        UserMessage original = (UserMessage) messages.get(lastUserIdx);
        ImageContent[] imageArray = imageContents.toArray(new ImageContent[0]);
        UserMessage withImages = UserMessage.from(original.singleText(), imageArray);

        List<ChatMessage> result = new ArrayList<>(messages);
        result.set(lastUserIdx, withImages);
        return result;
    }

    /**
     * 将 data URI（data:<mime>;base64,<data>）转换为 ImageContent。
     * 非 data URI（http/https URL）直接当作 URL 传入。
     */
    private ImageContent toImageContent(String dataUri) {
        if (dataUri == null || dataUri.isBlank()) {
            return null;
        }
        if (!dataUri.startsWith("data:")) {
            return ImageContent.from(Image.builder().url(dataUri).build());
        }
        int comma = dataUri.indexOf(',');
        if (comma < 0) {
            return null;
        }
        String meta = dataUri.substring(5, comma);
        String base64 = dataUri.substring(comma + 1);
        String mime = "image/png";
        int semi = meta.indexOf(';');
        if (semi > 0) {
            mime = meta.substring(0, semi);
        }
        return ImageContent.from(Image.builder().base64Data(base64).mimeType(mime).build());
    }

    /**
     * 从 ToolExecutionRequest 列表中识别图像生成工具调用（generate_image / dall-e），
     * 返回可插入 Markdown 的图片语法。保留原 extractImageFromToolCall 的业务逻辑。
     */
    private String extractImageFromToolCalls(List<ToolExecutionRequest> toolCalls) {
        try {
            ArrayNode arr = objectMapper.createArrayNode();
            for (ToolExecutionRequest req : toolCalls) {
                ObjectNode tc = objectMapper.createObjectNode();
                ObjectNode fn = objectMapper.createObjectNode();
                fn.put("name", req.name());
                fn.put("arguments", req.arguments());
                tc.set("function", fn);
                arr.add(tc);
            }
            return extractImageFromToolCall(arr);
        } catch (Exception e) {
            log.debug("Failed to extract image from tool calls: {}", e.getMessage());
            return null;
        }
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

    @SuppressWarnings("unused")
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
            connection.setConnectTimeout(props.getLocalFetch().getConnectSeconds() * 1000);
            connection.setReadTimeout(props.getLocalFetch().getReadSeconds() * 1000);

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
            Path uploadPath = Paths.get(uploadDir);
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
            connection.setConnectTimeout(props.getRemoteFetch().getConnectSeconds() * 1000);
            connection.setReadTimeout(props.getRemoteFetch().getReadSeconds() * 1000);

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
