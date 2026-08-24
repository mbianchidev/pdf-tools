package com.pdftools.operations.shared.image;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "pdf.operations.jpeg-validation")
public class JpegValidationProperties {

    private long maxProgressiveCoefficientBytes =
        64L * 1024L * 1024L;
    private long workerHeapBytes = 128L * 1024L * 1024L;
    private Duration workerTimeout = Duration.ofMinutes(2);

    public long getMaxProgressiveCoefficientBytes() {
        return maxProgressiveCoefficientBytes;
    }

    public void setMaxProgressiveCoefficientBytes(
            long maxProgressiveCoefficientBytes) {
        this.maxProgressiveCoefficientBytes =
            maxProgressiveCoefficientBytes;
    }

    public long getWorkerHeapBytes() {
        return workerHeapBytes;
    }

    public void setWorkerHeapBytes(long workerHeapBytes) {
        this.workerHeapBytes = workerHeapBytes;
    }

    public Duration getWorkerTimeout() {
        return workerTimeout;
    }

    public void setWorkerTimeout(Duration workerTimeout) {
        this.workerTimeout = workerTimeout;
    }
}
