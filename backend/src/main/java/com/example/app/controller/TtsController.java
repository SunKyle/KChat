package com.example.app.controller;

import com.example.app.dto.tts.*;
import com.example.app.service.TtsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Base64;
import java.util.List;

/**
 * TTS (Text-To-Speech) 控制器
 *
 * 提供文本朗读、音色管理等 REST API
 */
@RestController
@RequestMapping("/api/tts")
@RequiredArgsConstructor
@Slf4j
public class TtsController {

    private final TtsService ttsService;

    /**
     * 朗读文本（使用已注册音色）
     */
    @PostMapping("/speak")
    public ResponseEntity<byte[]> speak(
            @RequestBody SpeakRequest request,
            @RequestHeader(value = "X-User-Id", defaultValue = "default") String userId) {

        log.info("Speak request received, text length: {}, spkId: {}, userId: {}",
                request.getText() != null ? request.getText().length() : 0,
                request.getSpkId(), userId);

        TtsResult result = ttsService.speak(request.getText(), request.getSpkId(), userId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("audio/wav"));
        headers.set("X-Sample-Rate", String.valueOf(result.getSampleRate()));
        headers.set("X-Duration-S", String.valueOf(result.getDurationS()));

        return new ResponseEntity<>(result.getAudio(), headers, 200);
    }

    /**
     * 流式朗读（SSE）- 边生成边播放，显著降低首字节延迟
     */
    @PostMapping(value = "/speak/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter speakStream(
            @RequestBody SpeakRequest request,
            @RequestHeader(value = "X-User-Id", defaultValue = "default") String userId) {

        log.info("Stream speak request, text length: {}, spkId: {}, userId: {}",
                request.getText() != null ? request.getText().length() : 0,
                request.getSpkId(), userId);

        return ttsService.speakStream(request.getText(), request.getSpkId(), userId);
    }

    /**
     * 临时试听（使用临时 prompt 音频）
     */
    @PostMapping("/preview")
    public ResponseEntity<byte[]> preview(
            @RequestBody PreviewRequest request,
            @RequestHeader(value = "X-User-Id", defaultValue = "default") String userId) {

        log.info("Preview request received, text length: {}, has promptWav: {}",
                request.getText() != null ? request.getText().length() : 0,
                request.getPromptWavBase64() != null);

        byte[] promptWav = null;
        if (request.getPromptWavBase64() != null && !request.getPromptWavBase64().isBlank()) {
            promptWav = Base64.getDecoder().decode(request.getPromptWavBase64());
        }

        TtsResult result = ttsService.preview(
                request.getText(),
                request.getPromptText(),
                promptWav,
                userId
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("audio/wav"));
        headers.set("X-Sample-Rate", String.valueOf(result.getSampleRate()));

        return new ResponseEntity<>(result.getAudio(), headers, 200);
    }

    /**
     * 注册新音色
     */
    @PostMapping("/speakers")
    public ResponseEntity<SpeakerVo> registerSpeaker(
            @RequestParam("prompt_wav") MultipartFile promptWav,
            @RequestParam("name") String name,
            @RequestParam(value = "prompt_text", defaultValue = "") String promptText,
            @RequestHeader(value = "X-User-Id", defaultValue = "default") String userId) {

        log.info("Register speaker request: name={}, promptText={}, userId={}", name, promptText, userId);

        try {
            byte[] wavBytes = promptWav.getBytes();
            SpeakerVo speaker = ttsService.registerSpeaker(name, promptText, wavBytes, userId);
            return ResponseEntity.ok(speaker);
        } catch (Exception e) {
            log.error("Failed to register speaker: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * 列出当前用户的音色
     */
    @GetMapping("/speakers")
    public ResponseEntity<List<SpeakerVo>> listSpeakers(
            @RequestHeader(value = "X-User-Id", defaultValue = "default") String userId) {

        List<SpeakerVo> speakers = ttsService.listSpeakers(userId);
        return ResponseEntity.ok(speakers);
    }

    /**
     * 删除音色
     */
    @DeleteMapping("/speakers/{spkId}")
    public ResponseEntity<Void> deleteSpeaker(
            @PathVariable String spkId,
            @RequestHeader(value = "X-User-Id", defaultValue = "default") String userId) {

        log.info("Delete speaker request: spkId={}, userId={}", spkId, userId);

        try {
            ttsService.deleteSpeaker(spkId, userId);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            log.warn("Failed to delete speaker: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Failed to delete speaker: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取 TTS 服务健康状态
     */
    @GetMapping("/health")
    public ResponseEntity<CosyVoiceHealth> health() {
        CosyVoiceHealth health = ttsService.health();
        return ResponseEntity.ok(health);
    }
}
