package org.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 异步线程池配置 — 替代 Executors.newCachedThreadPool()
 *
 * 修复问题：
 * - 原 InterviewController 使用 newCachedThreadPool()，线程数无上限，无关闭机制
 * - 改为有界线程池，Spring 容器关闭时自动 shutdown
 */
@Configuration
public class AsyncConfig {

    @Bean(name = "sseExecutor", destroyMethod = "shutdown")
    public ExecutorService sseExecutor() {
        return new ThreadPoolExecutor(
                4,                      // 核心线程数
                16,                     // 最大线程数
                60L, TimeUnit.SECONDS,  // 空闲线程存活时间
                new LinkedBlockingQueue<>(100),  // 任务队列容量
                new ThreadPoolExecutor.CallerRunsPolicy()  // 队列满时由调用线程执行
        );
    }

    /**
     * AI 调用专用线程池（用于 HttpClient 异步请求）
     * 配置项：ai.http-threads（默认 4）
     */
    @Bean(name = "aiExecutor", destroyMethod = "shutdown")
    public ExecutorService aiExecutor(@Value("${ai.http-threads:4}") int threads) {
        return Executors.newFixedThreadPool(threads);
    }
}
