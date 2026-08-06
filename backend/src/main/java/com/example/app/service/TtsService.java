package com.example.app.service;

import com.example.app.dto.tts.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * TTS 服务接口
 */
public interface TtsService {

    /**
     * 朗读文本（使用已注册音色）
     */
    TtsResult speak(String text, String spkId, String userId);

    /**
     * 流式朗读（SSE），边生成边播放
     */
    SseEmitter speakStream(String text, String spkId, String userId);

    /**
     * 临时试听（使用临时 prompt 音频，不注册）
     */
    TtsResult preview(String text, String promptText, byte[] promptWav, String userId);

    /**
     * 注册新音色
     */
    SpeakerVo registerSpeaker(String name, String promptText, byte[] promptWav, String userId);

    /**
     * 列出用户的音色
     */
    List<SpeakerVo> listSpeakers(String userId);

    /**
     * 删除音色
     */
    void deleteSpeaker(String spkId, String userId);

    /**
     * 获取服务健康状态
     */
    CosyVoiceHealth health();
}
