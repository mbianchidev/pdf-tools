package com.pdftools.operations.markdownpdf;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "pdf.operations.markdown")
public class MarkdownProperties {

    private long maxInputBytes = 2L * 1024L * 1024L;
    private long maxBundleBytes = 16L * 1024L * 1024L;
    private int maxBundleEntries = 500;
    private int maxImages = 200;
    private long maxTotalImageBytes = 6L * 1024L * 1024L;
    private long workerHeapBytes = 256L * 1024L * 1024L;
    private Duration workerTimeout = Duration.ofMinutes(1);

    public long getMaxInputBytes() {
        return maxInputBytes;
    }

    public void setMaxInputBytes(long value) {
        maxInputBytes = value;
    }

    public long getMaxBundleBytes() {
        return maxBundleBytes;
    }

    public void setMaxBundleBytes(long value) {
        maxBundleBytes = value;
    }

    public int getMaxBundleEntries() {
        return maxBundleEntries;
    }

    public void setMaxBundleEntries(int value) {
        maxBundleEntries = value;
    }

    public int getMaxImages() {
        return maxImages;
    }

    public void setMaxImages(int value) {
        maxImages = value;
    }

    public long getMaxTotalImageBytes() {
        return maxTotalImageBytes;
    }

    public void setMaxTotalImageBytes(long value) {
        maxTotalImageBytes = value;
    }

    public long getWorkerHeapBytes() {
        return workerHeapBytes;
    }

    public void setWorkerHeapBytes(long value) {
        workerHeapBytes = value;
    }

    public Duration getWorkerTimeout() {
        return workerTimeout;
    }

    public void setWorkerTimeout(Duration value) {
        workerTimeout = value;
    }
}
