package com.pdftools.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Duration;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${cors.allowed-origins}")
    private String allowedOrigins;

    private final AsyncTaskExecutor applicationTaskExecutor;
    private final Duration asyncTimeout;

    public WebConfig(
            @Qualifier("applicationTaskExecutor") AsyncTaskExecutor applicationTaskExecutor,
            @Value("${pdf.download.async-timeout:2h}") Duration asyncTimeout) {
        this.applicationTaskExecutor = applicationTaskExecutor;
        this.asyncTimeout = asyncTimeout;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins.split(","))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setTaskExecutor(applicationTaskExecutor);
        configurer.setDefaultTimeout(asyncTimeout.toMillis());
    }
}
