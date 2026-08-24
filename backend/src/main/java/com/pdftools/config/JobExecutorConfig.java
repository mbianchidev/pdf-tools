package com.pdftools.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class JobExecutorConfig {

    @Bean("pdfJobExecutor")
    public TaskExecutor pdfJobExecutor(JobProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getExecutorCoreSize());
        executor.setMaxPoolSize(properties.getExecutorMaxSize());
        executor.setQueueCapacity(properties.getExecutorQueueCapacity());
        executor.setThreadNamePrefix("pdf-job-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }

    @Bean("applicationTaskExecutor")
    public AsyncTaskExecutor applicationTaskExecutor(
            @Value("${pdf.download.executor-core-size:4}") int coreSize,
            @Value("${pdf.download.executor-max-size:16}") int maxSize,
            @Value("${pdf.download.executor-queue-capacity:100}") int queueCapacity) {
        return executor(coreSize, maxSize, queueCapacity, "pdf-download-");
    }

    @Bean("legacyPdfExecutor")
    public AsyncTaskExecutor legacyPdfExecutor(
            @Value("${pdf.operations.split.legacy-executor-core-size:1}")
            int coreSize,
            @Value("${pdf.operations.split.legacy-executor-max-size:2}")
            int maxSize,
            @Value("${pdf.operations.split.legacy-executor-queue-capacity:4}")
            int queueCapacity) {
        return executor(coreSize, maxSize, queueCapacity, "pdf-legacy-");
    }

    private AsyncTaskExecutor executor(
            int coreSize,
            int maxSize,
            int queueCapacity,
            String threadPrefix) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(coreSize);
        executor.setMaxPoolSize(maxSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix(threadPrefix);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }
}
