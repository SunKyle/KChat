package com.example.app.client;

import com.example.app.config.CosyVoiceConfig;
import com.example.app.dto.tts.CosyVoiceHealth;
import com.example.app.dto.tts.SpeakerVo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * CosyVoice FastAPI 服务客户端
 *
 * 封装与本地 CosyVoice TTS 服务的 HTTP 通信
 */
@Component
@Slf4j
public class CosyVoiceClient {

    private final CosyVoiceConfig config;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final ExecutorService streamExecutor;

    public CosyVoiceClient(CosyVoiceConfig config, ObjectMapper objectMapper) {
        this.config = config;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .baseUrl(config.getBaseUrl())
                .build();
        this.streamExecutor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "cosyvoice-stream");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * 健康检查
     */
    @Retry(name = "cosyvoiceRetry")
    @CircuitBreaker(name = "cosyvoiceCB")
    public CosyVoiceHealth getHealth() {
        try {
            String response = restClient.get()
                    .uri("/health")
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .retrieve()
                    .body(String.class);

            if (response != null) {
                JsonNode root = objectMapper.readTree(response);
                if (root.get("code").asInt() == 0) {
                    JsonNode data = root.get("data");
                    return CosyVoiceHealth.builder()
                            .status(data.has("status") ? data.get("status").asText() : "unknown")
                            .modelDir(data.has("model_dir") ? data.get("model_dir").asText() : "")
                            .modelType(data.has("model_type") ? data.get("model_type").asText() : "")
                            .sampleRate(data.has("sample_rate") ? data.get("sample_rate").asInt() : 22050)
                            .device(data.has("device") ? data.get("device").asText() : "unknown")
                            .cudaAvailable(data.has("cuda_available") && data.get("cuda_available").asBoolean())
                            .speakers(data.has("speakers") ? data.get("speakers").asInt() : 0)
                            .queueSize(data.has("queue_size") ? data.get("queue_size").asInt() : 0)
                            .concurrency(data.has("concurrency") ? data.get("concurrency").asInt() : 1)
                            .build();
                }
            }
        } catch (Exception e) {
            log.error("Failed to get CosyVoice health: {}", e.getMessage());
        }
        return CosyVoiceHealth.builder().status("unavailable").build();
    }

    /**
     * 获取可用音色列表
     */
    @Retry(name = "cosyvoiceRetry")
    @CircuitBreaker(name = "cosyvoiceCB")
    public List<SpeakerVo> listSpeakers() {
        List<SpeakerVo> speakers = new ArrayList<>();
        try {
            String response = restClient.get()
                    .uri("/speakers")
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .retrieve()
                    .body(String.class);

            if (response != null) {
                JsonNode root = objectMapper.readTree(response);
                if (root.get("code").asInt() == 0) {
                    JsonNode data = root.get("data");
                    if (data.isArray()) {
                        for (JsonNode spk : data) {
                            speakers.add(SpeakerVo.builder()
                                    .spkId(spk.get("spk_id").asText())
                                    .source(spk.has("source") ? spk.get("source").asText() : "predefined")
                                    .build());
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to list speakers: {}", e.getMessage());
        }
        return speakers;
    }

    /**
     * 使用已注册音色进行零样本合成
     */
    @Retry(name = "cosyvoiceRetry")
    @CircuitBreaker(name = "cosyvoiceCB")
    public byte[] synthesizeZeroShot(String text, String spkId, double speed, boolean textFrontend) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("tts_text", text);
        body.add("zero_shot_spk_id", spkId);
        body.add("speed", String.valueOf(speed));
        body.add("text_frontend", String.valueOf(textFrontend));
        body.add("response_format", "wav");
        body.add("use_cache", String.valueOf(config.isUseCache()));

        return executeMultipartPost("/tts/zero-shot", body);
    }

    /**
     * 使用临时 prompt 音频进行零样本合成（试听用）
     */
    @Retry(name = "cosyvoiceRetry")
    @CircuitBreaker(name = "cosyvoiceCB")
    public byte[] synthesizeZeroShot(String text, String promptText, byte[] promptWav, double speed) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("tts_text", text);
        body.add("prompt_text", promptText);
        body.add("speed", String.valueOf(speed));
        body.add("text_frontend", String.valueOf(config.isDefaultTextFrontend()));
        body.add("response_format", "wav");
        body.add("use_cache", "false");

        // Add the wav file as a resource
        Resource wavResource = new ByteArrayResource(promptWav) {
            @Override
            public String getFilename() {
                return "prompt.wav";
            }
        };
        body.add("prompt_wav", wavResource);

        return executeMultipartPost("/tts/zero-shot", body);
    }

    /**
     * 注册新音色
     */
    @Retry(name = "cosyvoiceRetry")
    @CircuitBreaker(name = "cosyvoiceCB")
    public void registerSpeaker(String spkId, String promptText, byte[] promptWav) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("zero_shot_spk_id", spkId);
        body.add("prompt_text", promptText);

        Resource wavResource = new ByteArrayResource(promptWav) {
            @Override
            public String getFilename() {
                return "prompt.wav";
            }
        };
        body.add("prompt_wav", wavResource);

        try {
            String response = restClient.post()
                    .uri("/speakers/register")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            if (response != null) {
                JsonNode root = objectMapper.readTree(response);
                if (root.get("code").asInt() != 0) {
                    log.error("Failed to register speaker: {}", root.get("message").asText());
                    throw new RuntimeException("Failed to register speaker: " + root.get("message").asText());
                }
            }
        } catch (RestClientResponseException e) {
            log.error("HTTP error registering speaker: {}", e.getMessage());
            throw new RuntimeException("Failed to register speaker: " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            log.error("Failed to register speaker: {}", e.getMessage());
            throw new RuntimeException("Failed to register speaker", e);
        }
    }

    /**
     * 删除音色
     */
    @Retry(name = "cosyvoiceRetry")
    @CircuitBreaker(name = "cosyvoiceCB")
    public void deleteSpeaker(String spkId) {
        try {
            restClient.delete()
                    .uri("/speakers/{spkId}", spkId)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            log.error("HTTP error deleting speaker: {}", e.getMessage());
            throw new RuntimeException("Failed to delete speaker: " + e.getResponseBodyAsString(), e);
        }
    }

    /**
     * 执行 multipart/form-data POST 请求并返回 wav 字节
     */
    private byte[] executeMultipartPost(String path, MultiValueMap<String, Object> body) {
        try {
            return restClient.post()
                    .uri(path)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .accept(MediaType.parseMediaType("audio/wav"))
                    .body(body)
                    .retrieve()
                    .body(byte[].class);
        } catch (RestClientResponseException e) {
            log.error("HTTP error during CosyVoice synthesis: {}", e.getMessage());
            String errorBody = e.getResponseBodyAsString();
            log.error("Error body: {}", errorBody);
            throw new RuntimeException("CosyVoice synthesis failed: " + errorBody, e);
        }
    }

    /**
     * 流式合成：通过 SSE 将 CosyVoice 的流式音频分片推送给前端。
     *
     * CosyVoice 的 stream=true 接口返回 chunked audio/wav，
     * 这里将每个 chunk 包装成 SSE event (base64编码) 推送给前端，
     * 前端用 MediaSource API 实现边收边播。
     */
    @Retry(name = "cosyvoiceRetry")
    @CircuitBreaker(name = "cosyvoiceCB")
    public void synthesizeStream(String text, String spkId, SseEmitter emitter) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("tts_text", text);
        body.add("zero_shot_spk_id", spkId);
        body.add("speed", String.valueOf(config.getDefaultSpeed()));
        body.add("text_frontend", String.valueOf(config.isDefaultTextFrontend()));
        body.add("response_format", "wav");
        body.add("stream", "true");
        body.add("use_cache", "false");

        streamExecutor.submit(() -> {
            try {
                log.info("Starting stream synthesis for text length: {}, spkId: {}", text.length(), spkId);

                InputStream inputStream = restClient.post()
                        .uri("/tts/zero-shot")
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .accept(MediaType.parseMediaType("audio/wav"))
                        .body(body)
                        .exchange((request, response) -> {
                            if (response.getStatusCode().isError()) {
                                throw new RuntimeException("CosyVoice stream failed: HTTP " + response.getStatusCode());
                            }
                            return response.getBody();
                        });

                if (inputStream == null) {
                    emitter.send(SseEmitter.event().name("error").data("No audio stream"));
                    emitter.complete();
                    return;
                }

                byte[] buffer = new byte[4096];
                int bytesRead;
                long totalBytes = 0;

                // 发送 start 事件
                emitter.send(SseEmitter.event().name("start").data(""));

                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    byte[] chunk = new byte[bytesRead];
                    System.arraycopy(buffer, 0, chunk, 0, bytesRead);
                    String base64Chunk = java.util.Base64.getEncoder().encodeToString(chunk);

                    emitter.send(SseEmitter.event().name("audio").data(base64Chunk));
                    totalBytes += bytesRead;
                }

                // 发送 done 事件
                emitter.send(SseEmitter.event().name("done").data(totalBytes));
                emitter.complete();
                log.info("Stream synthesis completed, total bytes: {}", totalBytes);

            } catch (IOException e) {
                log.warn("Stream synthesis interrupted: {}", e.getMessage());
                emitter.complete();
            } catch (Exception e) {
                log.error("Stream synthesis failed: {}", e.getMessage(), e);
                try {
                    emitter.send(SseEmitter.event().name("error").data(e.getMessage()));
                } catch (IOException ignored) {
                }
                emitter.completeWithError(e);
            }
        });
    }
}
