package com.pdftools.operations.watermark;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pdf.operations.watermark")
public class WatermarkProperties {

    private long maxImageBytes = 10L * 1024L * 1024L;
    private int maxImageDimension = 4096;
    private long maxImagePixels = 4_000_000;

    public long getMaxImageBytes() {
        return maxImageBytes;
    }

    public void setMaxImageBytes(long maxImageBytes) {
        this.maxImageBytes = maxImageBytes;
    }

    public int getMaxImageDimension() {
        return maxImageDimension;
    }

    public void setMaxImageDimension(int maxImageDimension) {
        this.maxImageDimension = maxImageDimension;
    }

    public long getMaxImagePixels() {
        return maxImagePixels;
    }

    public void setMaxImagePixels(long maxImagePixels) {
        this.maxImagePixels = maxImagePixels;
    }
}
