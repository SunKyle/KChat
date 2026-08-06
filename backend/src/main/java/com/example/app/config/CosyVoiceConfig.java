package com.example.app.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * CosyVoice TTS 服务配置
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "cosyvoice")
public class CosyVoiceConfig {

    /**
     * CosyVoice FastAPI 服务地址
     */
    private String baseUrl = "http://127.0.0.1:50000";

    /**
     * 默认合成模式: sft | zero-shot | cross-lingual | instruct2
     */
    private String defaultMode = "zero-shot";

    /**
     * 默认预注册的音色 ID
     */
    private String defaultSpkId = "";

    /**
     * 默认语速 (0.5~2.0)
     */
    private double defaultSpeed = 1.0;

    /**
     * 是否启用文本前端归一化
     */
    private boolean defaultTextFrontend = true;

    /**
     * 是否启用 CosyVoice 服务端缓存
     */
    private boolean useCache = true;

    /**
     * 连接超时（毫秒）
     */
    private int connectTimeoutMs = 5000;

    /**
     * 读取超时（毫秒），CPU 推理较慢，给足 3 分钟
     */
    private int readTimeoutMs = 180000;

    /**
     * 最大文本长度（与 CosyVoice 限制对齐）
     */
    private int maxTextLength = 2000;
}
