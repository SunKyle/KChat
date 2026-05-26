package com.example.app.client;

import com.example.app.entity.ModelConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class OpenAICompatibleClient {

    private final ObjectMapper objectMapper;

    public void streamChatCompletion(
            String modelId,
            String baseUrl,
            String apiKey,
            String prompt,
            SseEmitter emitter) {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.MINUTES)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();

        try {
            JsonNode requestBody = objectMapper.createObjectNode()
                    .put("model", modelId)
                    .put("prompt", prompt)
                    .put("stream", true)
                    .put("max_tokens", 4096)
                    .put("temperature", 0.7);

            RequestBody body = RequestBody.create(
                    objectMapper.writeValueAsString(requestBody),
                    MediaType.parse("application/json"));

            Request request = new Request.Builder()
                    .url(baseUrl)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .post(body)
                    .build();

            log.info("Sending request to: {}", baseUrl);

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
                        try {
                            emitter.completeWithError(new RuntimeException(
                                    "API request failed: " + response.code()));
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

                        while ((bytesRead = responseBody.byteStream().read(buffer)) != -1) {
                            String chunk = new String(buffer, 0, bytesRead);
                            String[] lines = chunk.split("\n");

                            for (String line : lines) {
                                if (line.trim().isEmpty())
                                    continue;
                                if (line.startsWith("data: ")) {
                                    String data = line.substring(6);
                                    if (data.equals("[DONE]")) {
                                        emitter.complete();
                                        return;
                                    }
                                    try {
                                        emitter.send(SseEmitter.event()
                                                .name("message")
                                                .data(data));
                                    } catch (Exception e) {
                                        log.error("Failed to send SSE event", e);
                                        return;
                                    }
                                }
                            }
                        }
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
}