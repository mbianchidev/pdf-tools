package com.pdftools.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;

@ConfigurationProperties(prefix = "pdf.jobs")
public class JobProperties {

    private Duration retention = Duration.ofHours(2);
    private Duration cleanupInterval = Duration.ofMinutes(15);
    private Duration dispatchInterval = Duration.ofSeconds(1);
    private Duration orphanGrace = Duration.ofMinutes(30);
    private Duration leaseDuration = Duration.ofMinutes(2);
    private Duration leaseRenewInterval = Duration.ofSeconds(30);
    private int maxFiles = 20;
    private long maxFileSizeBytes = 100L * 1024L * 1024L;
    private Path workRoot = Path.of("/tmp/pdf-work");
    private int executorCoreSize = 2;
    private int executorMaxSize = 4;
    private int executorQueueCapacity = 20;
    private Set<String> enabledOperations = new LinkedHashSet<>();

    public Duration getRetention() {
        return retention;
    }

    public void setRetention(Duration retention) {
        this.retention = retention;
    }

    public Duration getCleanupInterval() {
        return cleanupInterval;
    }

    public void setCleanupInterval(Duration cleanupInterval) {
        this.cleanupInterval = cleanupInterval;
    }

    public Duration getDispatchInterval() {
        return dispatchInterval;
    }

    public void setDispatchInterval(Duration dispatchInterval) {
        this.dispatchInterval = dispatchInterval;
    }

    public Duration getOrphanGrace() {
        return orphanGrace;
    }

    public void setOrphanGrace(Duration orphanGrace) {
        this.orphanGrace = orphanGrace;
    }

    public Duration getLeaseDuration() {
        return leaseDuration;
    }

    public void setLeaseDuration(Duration leaseDuration) {
        this.leaseDuration = leaseDuration;
    }

    public Duration getLeaseRenewInterval() {
        return leaseRenewInterval;
    }

    public void setLeaseRenewInterval(Duration leaseRenewInterval) {
        this.leaseRenewInterval = leaseRenewInterval;
    }

    public int getMaxFiles() {
        return maxFiles;
    }

    public void setMaxFiles(int maxFiles) {
        this.maxFiles = maxFiles;
    }

    public long getMaxFileSizeBytes() {
        return maxFileSizeBytes;
    }

    public void setMaxFileSizeBytes(long maxFileSizeBytes) {
        this.maxFileSizeBytes = maxFileSizeBytes;
    }

    public Path getWorkRoot() {
        return workRoot;
    }

    public void setWorkRoot(Path workRoot) {
        this.workRoot = workRoot;
    }

    public int getExecutorCoreSize() {
        return executorCoreSize;
    }

    public void setExecutorCoreSize(int executorCoreSize) {
        this.executorCoreSize = executorCoreSize;
    }

    public int getExecutorMaxSize() {
        return executorMaxSize;
    }

    public void setExecutorMaxSize(int executorMaxSize) {
        this.executorMaxSize = executorMaxSize;
    }

    public int getExecutorQueueCapacity() {
        return executorQueueCapacity;
    }

    public void setExecutorQueueCapacity(int executorQueueCapacity) {
        this.executorQueueCapacity = executorQueueCapacity;
    }

    public Set<String> getEnabledOperations() {
        return enabledOperations;
    }

    public void setEnabledOperations(Set<String> enabledOperations) {
        this.enabledOperations = enabledOperations;
    }
}
