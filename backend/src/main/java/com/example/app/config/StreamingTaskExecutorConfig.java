package com.example.app.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 流式请求专用线程池。
 *
 * <p>背景：流式 Agent 路径里 {@code latch.await(10min)} 会长时间占用线程，
 * 默认 {@code CompletableFuture.runAsync()} 走 {@code ForkJoinPool.commonPool()}（默认大小
 * CPU 核数 - 1），并发 7 条流式请求就能耗尽线程池，后续请求无限排队。
 *
 * <p>本配置提供一个独立线程池，专门承载流式 Agent 异步任务，与 commonPool 隔离：
 * <ul>
 *   <li>corePoolSize=20：基础并发能力</li>
 *   <li>maxPoolSize=50：突发流量上限</li>
 *   <li>queueCapacity=100：请求缓冲</li>
 *   <li>CallerRunsPolicy：队列满时让调用者线程跑，至少不丢任务</li>
 * </ul>
 *
 * <p>非流式请求和工具执行不使用此池，避免相互影响。
 */
@Configuration
@Slf4j
public class StreamingTaskExecutorConfig {

    @Bean(name = "streamingTaskExecutor", destroyMethod = "shutdown")
    public ThreadPoolTaskExecutor streamingTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(20);
        executor.setMaxPoolSize(50);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("streaming-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        log.info("[StreamingTaskExecutor] initialized: core=20, max=50, queue=100, prefix=streaming-");
        return executor;
    }
}
