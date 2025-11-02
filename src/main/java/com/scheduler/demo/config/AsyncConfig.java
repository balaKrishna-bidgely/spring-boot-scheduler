package com.scheduler.demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.beans.factory.annotation.Value;

import java.util.concurrent.Executor;

@Configuration
public class AsyncConfig {

    @Value("${app.threadpool.core:10}")
    private int corePool;

    @Value("${app.threadpool.max:30}")
    private int maxPool;

    @Value("${app.threadpool.queue-capacity:100}")
    private int queueCapacity;

    @Bean(name = "notificationExecutor")
    public Executor notificationExecutor() {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(corePool);
        exec.setMaxPoolSize(maxPool);
        exec.setQueueCapacity(queueCapacity);
        exec.setThreadNamePrefix("notification-exec-");
        exec.initialize();
        return exec;
    }
}

