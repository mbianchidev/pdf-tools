package com.pdftools.operations.jpgpdf;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "pdf.operations.jpg-to-pdf")
public class JpgToPdfProperties {

    private int maxImages = 100;
    private long maxTotalInputBytes = 100L * 1024L * 1024L;
    private int maxImageDimension = 16_384;
    private long maxPixelsPerImage = 50_000_000;
    private long maxTotalPixels = 500_000_000;
    private long maxOutputBytes = 128L * 1024L * 1024L;
    private int fitImageDpi = 96;
    private float maxFitPagePoints = 1440;
    private long maxProgressiveCoefficientBytes =
        64L * 1024L * 1024L;
    private long validationWorkerHeapBytes = 128L * 1024L * 1024L;
    private Duration validationWorkerTimeout = Duration.ofMinutes(2);

    public int getMaxImages() {
        return maxImages;
    }

    public void setMaxImages(int maxImages) {
        this.maxImages = maxImages;
    }

    public long getMaxTotalInputBytes() {
        return maxTotalInputBytes;
    }

    public void setMaxTotalInputBytes(long maxTotalInputBytes) {
        this.maxTotalInputBytes = maxTotalInputBytes;
    }

    public int getMaxImageDimension() {
        return maxImageDimension;
    }

    public void setMaxImageDimension(int maxImageDimension) {
        this.maxImageDimension = maxImageDimension;
    }

    public long getMaxPixelsPerImage() {
        return maxPixelsPerImage;
    }

    public void setMaxPixelsPerImage(long maxPixelsPerImage) {
        this.maxPixelsPerImage = maxPixelsPerImage;
    }

    public long getMaxTotalPixels() {
        return maxTotalPixels;
    }

    public void setMaxTotalPixels(long maxTotalPixels) {
        this.maxTotalPixels = maxTotalPixels;
    }

    public long getMaxOutputBytes() {
        return maxOutputBytes;
    }

    public void setMaxOutputBytes(long maxOutputBytes) {
        this.maxOutputBytes = maxOutputBytes;
    }

    public int getFitImageDpi() {
        return fitImageDpi;
    }

    public void setFitImageDpi(int fitImageDpi) {
        this.fitImageDpi = fitImageDpi;
    }

    public float getMaxFitPagePoints() {
        return maxFitPagePoints;
    }

    public void setMaxFitPagePoints(float maxFitPagePoints) {
        this.maxFitPagePoints = maxFitPagePoints;
    }

    public long getMaxProgressiveCoefficientBytes() {
        return maxProgressiveCoefficientBytes;
    }

    public void setMaxProgressiveCoefficientBytes(
            long maxProgressiveCoefficientBytes) {
        this.maxProgressiveCoefficientBytes =
            maxProgressiveCoefficientBytes;
    }

    public long getValidationWorkerHeapBytes() {
        return validationWorkerHeapBytes;
    }

    public void setValidationWorkerHeapBytes(
            long validationWorkerHeapBytes) {
        this.validationWorkerHeapBytes = validationWorkerHeapBytes;
    }

    public Duration getValidationWorkerTimeout() {
        return validationWorkerTimeout;
    }

    public void setValidationWorkerTimeout(
            Duration validationWorkerTimeout) {
        this.validationWorkerTimeout = validationWorkerTimeout;
    }
}
