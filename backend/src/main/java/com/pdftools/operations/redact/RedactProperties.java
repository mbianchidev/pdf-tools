package com.pdftools.operations.redact;

import com.pdftools.operations.shared.pdf.PdfPageTreeLimits;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "pdf.operations.redact")
public class RedactProperties implements PdfPageTreeLimits {

    private int maxAreas = 500;
    private int maxAreasPerPage = 100;
    private int maxDocumentPages = 1000;
    private int renderDpi = 200;
    private int jpegQuality = 95;
    private long maxPixelsPerPage = 20_000_000;
    private int maxImageDimension = 16_384;
    private long maxImageBytes = 50L * 1024L * 1024L;
    private long maxTotalImageBytes = 512L * 1024L * 1024L;
    private long maxOutputBytes = 512L * 1024L * 1024L;
    private int maxPageTreeNodes = 10_000;
    private int maxPageTreeDepth = 32;
    private int maxContentStreamsPerPage = 1000;
    private long workerHeapBytes = 256L * 1024L * 1024L;
    private Duration workerTimeout = Duration.ofMinutes(5);

    public int getMaxAreas() {
        return maxAreas;
    }

    public void setMaxAreas(int maxAreas) {
        this.maxAreas = maxAreas;
    }

    public int getMaxAreasPerPage() {
        return maxAreasPerPage;
    }

    public void setMaxAreasPerPage(int maxAreasPerPage) {
        this.maxAreasPerPage = maxAreasPerPage;
    }

    public int getMaxDocumentPages() {
        return maxDocumentPages;
    }

    public void setMaxDocumentPages(int maxDocumentPages) {
        this.maxDocumentPages = maxDocumentPages;
    }

    public int getRenderDpi() {
        return renderDpi;
    }

    public void setRenderDpi(int renderDpi) {
        this.renderDpi = renderDpi;
    }

    public int getJpegQuality() {
        return jpegQuality;
    }

    public void setJpegQuality(int jpegQuality) {
        this.jpegQuality = jpegQuality;
    }

    public long getMaxPixelsPerPage() {
        return maxPixelsPerPage;
    }

    public void setMaxPixelsPerPage(long maxPixelsPerPage) {
        this.maxPixelsPerPage = maxPixelsPerPage;
    }

    public int getMaxImageDimension() {
        return maxImageDimension;
    }

    public void setMaxImageDimension(int maxImageDimension) {
        this.maxImageDimension = maxImageDimension;
    }

    public long getMaxImageBytes() {
        return maxImageBytes;
    }

    public void setMaxImageBytes(long maxImageBytes) {
        this.maxImageBytes = maxImageBytes;
    }

    public long getMaxTotalImageBytes() {
        return maxTotalImageBytes;
    }

    public void setMaxTotalImageBytes(long maxTotalImageBytes) {
        this.maxTotalImageBytes = maxTotalImageBytes;
    }

    public long getMaxOutputBytes() {
        return maxOutputBytes;
    }

    public void setMaxOutputBytes(long maxOutputBytes) {
        this.maxOutputBytes = maxOutputBytes;
    }

    public void setMaxPageTreeNodes(int maxPageTreeNodes) {
        this.maxPageTreeNodes = maxPageTreeNodes;
    }

    public void setMaxPageTreeDepth(int maxPageTreeDepth) {
        this.maxPageTreeDepth = maxPageTreeDepth;
    }

    public void setMaxContentStreamsPerPage(
            int maxContentStreamsPerPage) {
        this.maxContentStreamsPerPage = maxContentStreamsPerPage;
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

    @Override
    public int maxPages() {
        return maxDocumentPages;
    }

    @Override
    public int maxPageTreeNodes() {
        return maxPageTreeNodes;
    }

    @Override
    public int maxPageTreeDepth() {
        return maxPageTreeDepth;
    }

    @Override
    public int maxContentStreamsPerPage() {
        return maxContentStreamsPerPage;
    }
}
