
package com.example.app.config;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
@Slf4j
public class AsyncConfig {

    private ExecutorService streamingExecutorService;

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
            new ThreadPoolExecutor.CallerRunsPolicy()
        );
        
        log.info("Streaming executor service initialized with core=2, max=10, queue=100");
        return streamingExecutorService;
    }

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
