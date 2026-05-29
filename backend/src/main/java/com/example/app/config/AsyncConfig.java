
package com.example.app.config;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 异步任务执行器配置
 *
 * 主要用于 LLM 流式响应的处理
 */
@Configuration
@Slf4j
public class AsyncConfig {

    private ExecutorService streamingExecutorService;

    /**
     * 流式响应执行器
     *
     * 设计决策：
     * 1. 核心线程数 2：保证基础处理能力
     * 2. 最大线程数 10：应对突发流量
     * 3. 队列容量 100：缓冲请求避免立即拒绝
     * 4. 拒绝策略 CallerRunsPolicy：队列满时由调用线程执行，防止数据丢失
     * 5. 守护线程：JVM 退出时自动终止，避免资源泄漏
     *
     * 并发风险：
     * - 队列溢出时可能导致内存占用过高
     * - 拒绝策略是 CallerRunsPolicy 可能阻塞 HTTP 线程池
     *
     * @return 配置好的 ExecutorService
     */
    @Bean
    public ExecutorService streamingExecutorService() {
        ThreadFactory threadFactory = new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger(0);

            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r);
                thread.setName("streaming-" + counter.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            }
        };

        streamingExecutorService = new ThreadPoolExecutor(
                2,
                10,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(100),
                threadFactory,
                new ThreadPoolExecutor.CallerRunsPolicy());

        log.info("Streaming executor service initialized with core=2, max=10, queue=100");
        return streamingExecutorService;
    }

    /**
     * 优雅关闭执行器
     *
     * 关闭流程：
     * 1. 先调用 shutdown() 停止接收新任务
     * 2. 等待 30 秒让已提交任务完成
     * 3. 如果超时，调用 shutdownNow() 强制终止
     *
     * 技术债务：
     * - 当前实现未处理强制终止时可能丢失未完成任务
     */
    @PreDestroy
    public void shutdown() {
        if (streamingExecutorService != null) {
            log.info("Shutting down streaming executor service...");
            streamingExecutorService.shutdown();
            try {
                if (!streamingExecutorService.awaitTermination(30, TimeUnit.SECONDS)) {
                    log.warn("Executor service did not terminate gracefully, forcing shutdown");
                    streamingExecutorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                log.warn("Shutdown interrupted", e);
                streamingExecutorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}
