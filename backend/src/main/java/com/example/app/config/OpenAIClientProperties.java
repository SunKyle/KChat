package com.example.app.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAI 兼容客户端配置
 *
 * 覆盖同步/流式对话温度、默认 max_tokens、
 * SD WebUI 采样参数，以及多种调用路径的超时配置。
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "openai-client")
public class OpenAIClientProperties {

    /**
     * 同步（非流式）对话的 temperature 值
     * 较低的 temperature 会让回答更稳定、聚焦
     */
    private double syncTemperature = 0.3;

    /**
     * 流式对话的 temperature 值
     * 略高于同步，使流式生成的内容更丰富
     */
    private double streamTemperature = 0.7;

    /**
     * 默认 max_tokens：单次对话响应的最大 token 数
     */
    private int defaultMaxTokens = 4096;

    /**
     * 流式响应读取缓冲区大小（字节）
     */
    private int streamBufferSize = 8192;

    private Sd sd = new Sd();
    private Timeout imageGen = new Timeout(30, 60, 30);
    private Timeout sdWebui = new Timeout(30, 120, 30);
    private Timeout chat = new Timeout(30, 120, 30);
    private Timeout multimodal = new Timeout(30, 180, 30);
    private Timeout stream = new Timeout(30, 300, 30);
    private Timeout localFetch = new Timeout(5, 5, 5);
    private Timeout remoteFetch = new Timeout(10, 30, 10);

    @Data
    public static class Sd {
        /**
         * Stable Diffusion 采样步数
         */
        private int steps = 20;
        /**
         * CFG Scale：提示词相关性控制
         */
        private double cfgScale = 7.0;
        /**
         * img2img 去噪强度（0~1）
         */
        private double denoisingStrength = 0.75;
    }

    @Data
    public static class Timeout {
        private int connectSeconds;
        private int readSeconds;
        private int writeSeconds;

        public Timeout() {}

        public Timeout(int connectSeconds, int readSeconds, int writeSeconds) {
            this.connectSeconds = connectSeconds;
            this.readSeconds = readSeconds;
            this.writeSeconds = writeSeconds;
        }
    }
}
