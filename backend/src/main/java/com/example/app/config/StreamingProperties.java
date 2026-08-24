package com.example.app.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 流式响应超时配置。
 *
 * <p>用于消除 SSE emitter 超时与 LLM 等待时间不匹配的隐患：
 * <ul>
 *   <li>{@link #sseTimeoutMs}：SSE 连接超时，必须严格大于 {@code agentStreamingTimeoutMs}
 *       ——否则 LLM 还在等回调时 emitter 已超时关闭，回调写入会失败，
 *       且用户看到的是 SSE timeout 而非真正的 LLM timeout。</li>
 *   <li>{@link #agentStreamingTimeoutMs}：Agent 流式 LLM 单次响应的等待上限，
 *       对应 {@code ModelRoutingStage.executeWithToolsStreaming} 内的 {@code latch.await}。</li>
 * </ul>
 *
 * <p>启动时会校验 {@code sseTimeoutMs > agentStreamingTimeoutMs}，否则启动失败（fail-fast）。
 */
@Data
@Slf4j
@Configuration
@ConfigurationProperties(prefix = "streaming")
public class StreamingProperties {

    /**
     * SSE emitter 超时（毫秒）。默认 12 分钟。
     * <p>必须大于 {@code agentStreamingTimeoutMs}，留出 LLM 完成回调的窗口。
     */
    private long sseTimeoutMs = 12 * 60 * 1000L;

    /**
     * Agent 流式 LLM 单次响应等待上限（毫秒）。默认 10 分钟。
     * <p>对应 {@code ModelRoutingStage.executeWithToolsStreaming} 中 {@code latch.await} 的超时。
     */
    private long agentStreamingTimeoutMs = 10 * 60 * 1000L;

    @PostConstruct
    public void validate() {
        if (sseTimeoutMs <= agentStreamingTimeoutMs) {
            throw new IllegalStateException(String.format(
                    "streaming.sse-timeout-ms (%d) must be strictly greater than "
                            + "streaming.agent-streaming-timeout-ms (%d) to keep SSE alive "
                            + "while LLM is still responding",
                    sseTimeoutMs, agentStreamingTimeoutMs));
        }
        log.info("[Streaming] sseTimeoutMs={}ms, agentStreamingTimeoutMs={}ms (buffer={}ms)",
                sseTimeoutMs, agentStreamingTimeoutMs, sseTimeoutMs - agentStreamingTimeoutMs);
    }
}
