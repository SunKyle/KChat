package com.example.app.service.impl;

import com.example.app.client.CosyVoiceClient;
import com.example.app.config.CosyVoiceConfig;
import com.example.app.dto.tts.*;
import com.example.app.entity.TtsSpeaker;
import com.example.app.repository.TtsSpeakerRepository;
import com.example.app.service.TtsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TtsServiceImpl implements TtsService {

    private final CosyVoiceClient cosyVoiceClient;
    private final CosyVoiceConfig config;
    private final TtsSpeakerRepository speakerRepository;

    @Override
    public TtsResult speak(String text, String spkId, String userId) {
        checkEnabled();
        validateText(text);

        // 如果未指定 spkId，使用默认配置
        String targetSpkId = spkId;
        if (targetSpkId == null || targetSpkId.isBlank()) {
            targetSpkId = config.getDefaultSpkId();
        }

        // 默认音色仍为空时，自动从 CosyVoice 取第一个可用音色
        if (targetSpkId == null || targetSpkId.isBlank()) {
            List<SpeakerVo> remoteSpeakers = cosyVoiceClient.listSpeakers();
            if (!remoteSpeakers.isEmpty()) {
                targetSpkId = remoteSpeakers.get(0).getSpkId();
                log.info("No default spkId configured, fallback to first CosyVoice speaker: {}", targetSpkId);
            }
        }

        if (targetSpkId == null || targetSpkId.isBlank()) {
            throw new IllegalArgumentException("未配置默认音色，请先在声音设置中添加音色，或明确指定 spkId");
        }

        // 权限校验：确保 spkId 属于当前用户或为系统默认
        TtsSpeaker speaker = speakerRepository.findById(targetSpkId)
                .orElse(null);
        if (speaker != null && !speaker.getOwnerUserId().equals(userId)) {
            throw new IllegalArgumentException("无权使用该音色");
        }

        log.info("Synthesizing speech for text length: {}, spkId: {}", text.length(), targetSpkId);

        byte[] audioBytes = cosyVoiceClient.synthesizeZeroShot(
                text,
                targetSpkId,
                config.getDefaultSpeed(),
                config.isDefaultTextFrontend()
        );

        return TtsResult.builder()
                .audio(audioBytes)
                .format("wav")
                .sampleRate(22050) // CosyVoice-300M default
                .build();
    }

    @Override
    public SseEmitter speakStream(String text, String spkId, String userId) {
        checkEnabled();
        validateText(text);

        String targetSpkId = spkId;
        if (targetSpkId == null || targetSpkId.isBlank()) {
            targetSpkId = config.getDefaultSpkId();
        }
        if (targetSpkId == null || targetSpkId.isBlank()) {
            List<SpeakerVo> remoteSpeakers = cosyVoiceClient.listSpeakers();
            if (!remoteSpeakers.isEmpty()) {
                targetSpkId = remoteSpeakers.get(0).getSpkId();
            }
        }
        if (targetSpkId == null || targetSpkId.isBlank()) {
            throw new IllegalArgumentException("未配置默认音色，请先在声音设置中添加音色，或明确指定 spkId");
        }

        // 权限校验
        TtsSpeaker speaker = speakerRepository.findById(targetSpkId).orElse(null);
        if (speaker != null && !speaker.getOwnerUserId().equals(userId)) {
            throw new IllegalArgumentException("无权使用该音色");
        }

        log.info("Stream synthesizing for text length: {}, spkId: {}", text.length(), targetSpkId);

        SseEmitter emitter = new SseEmitter(300_000L); // 5 min timeout
        emitter.onCompletion(() -> log.info("Stream emitter completed"));
        emitter.onTimeout(() -> log.warn("Stream emitter timeout for text: {}", text.length()));
        emitter.onError(e -> log.error("Stream emitter error: {}", e.getMessage()));

        cosyVoiceClient.synthesizeStream(text, targetSpkId, emitter);
        return emitter;
    }

    @Override
    public TtsResult preview(String text, String promptText, byte[] promptWav, String userId) {
        checkEnabled();
        validateText(text);

        if (promptWav == null || promptWav.length == 0) {
            throw new IllegalArgumentException("请提供 prompt 音频文件");
        }

        log.info("Preview synthesizing for text length: {}", text.length());

        byte[] audioBytes = cosyVoiceClient.synthesizeZeroShot(
                text,
                promptText != null ? promptText : "",
                promptWav,
                config.getDefaultSpeed()
        );

        return TtsResult.builder()
                .audio(audioBytes)
                .format("wav")
                .sampleRate(22050)
                .build();
    }

    @Override
    @Transactional
    public SpeakerVo registerSpeaker(String name, String promptText, byte[] promptWav, String userId) {
        checkEnabled();
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("音色名称不能为空");
        }
        if (promptWav == null || promptWav.length == 0) {
            throw new IllegalArgumentException("请提供音色参考音频");
        }

        // 生成唯一 spkId
        String spkId = "spk_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        log.info("Registering new speaker: name={}, spkId={}, userId={}", name, spkId, userId);

        // 调用 CosyVoice 注册音色
        cosyVoiceClient.registerSpeaker(spkId, promptText != null ? promptText : "", promptWav);

        // 保存元数据到数据库
        TtsSpeaker speaker = TtsSpeaker.builder()
                .spkId(spkId)
                .name(name)
                .promptText(promptText)
                .ownerUserId(userId != null ? userId : "default")
                .source("zero_shot")
                .createdAt(LocalDateTime.now())
                .build();

        speakerRepository.save(speaker);

        return convertToVo(speaker);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SpeakerVo> listSpeakers(String userId) {
        checkEnabled();
        List<TtsSpeaker> speakers = speakerRepository.findByOwnerUserId(userId);
        return speakers.stream()
                .map(this::convertToVo)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteSpeaker(String spkId, String userId) {
        checkEnabled();
        TtsSpeaker speaker = speakerRepository.findById(spkId)
                .orElseThrow(() -> new IllegalArgumentException("音色不存在: " + spkId));

        if (!speaker.getOwnerUserId().equals(userId)) {
            throw new IllegalArgumentException("无权删除该音色");
        }

        // 从 CosyVoice 服务端删除
        try {
            cosyVoiceClient.deleteSpeaker(spkId);
        } catch (Exception e) {
            log.warn("Failed to delete speaker from CosyVoice: {}, but will still remove from DB", e.getMessage());
        }

        // 从数据库删除
        speakerRepository.delete(speaker);
        log.info("Speaker deleted: {}", spkId);
    }

    @Override
    public CosyVoiceHealth health() {
        if (!config.isEnabled()) {
            return CosyVoiceHealth.builder().status("disabled").build();
        }
        return cosyVoiceClient.getHealth();
    }

    private void checkEnabled() {
        if (!config.isEnabled()) {
            throw new IllegalStateException("CosyVoice 已关闭");
        }
    }

    private void validateText(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("朗读文本不能为空");
        }
        if (text.length() > config.getMaxTextLength()) {
            throw new IllegalArgumentException("文本长度超过限制 (" + config.getMaxTextLength() + " 字)");
        }
    }

    private SpeakerVo convertToVo(TtsSpeaker speaker) {
        return SpeakerVo.builder()
                .spkId(speaker.getSpkId())
                .name(speaker.getName())
                .promptText(speaker.getPromptText())
                .source(speaker.getSource())
                .ownerUserId(speaker.getOwnerUserId())
                .createdAt(speaker.getCreatedAt())
                .build();
    }
}
