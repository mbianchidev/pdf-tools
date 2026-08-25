package com.pdftools.operations.edit;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pdf.operations.edit")
public class EditProperties {

    private int maxElements = 500;
    private int maxImages = 10;
    private long maxImageBytes = 10L * 1024L * 1024L;
    private long maxTotalDecodedImageBytes = 64L * 1024L * 1024L;

    public int getMaxElements() {
        return maxElements;
    }

    public void setMaxElements(int maxElements) {
        this.maxElements = maxElements;
    }

    public int getMaxImages() {
        return maxImages;
    }

    public void setMaxImages(int maxImages) {
        this.maxImages = maxImages;
    }

    public long getMaxImageBytes() {
        return maxImageBytes;
    }

    public void setMaxImageBytes(long maxImageBytes) {
        this.maxImageBytes = maxImageBytes;
    }

    public long getMaxTotalDecodedImageBytes() {
        return maxTotalDecodedImageBytes;
    }

    public void setMaxTotalDecodedImageBytes(
            long maxTotalDecodedImageBytes) {
        this.maxTotalDecodedImageBytes = maxTotalDecodedImageBytes;
    }
}
