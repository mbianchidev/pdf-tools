package com.pdftools.operations.pdfjpg;

import com.pdftools.operations.shared.pdf.PdfPageTreeLimits;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "pdf.operations.pdf-to-jpg")
public class PdfToJpgProperties implements PdfPageTreeLimits {

    private int maxDocumentPages = 1000;
    private int maxSelectedPages = 500;
    private int minDpi = 72;
    private int maxDpi = 300;
    private long maxPixelsPerPage = 20_000_000;
    private int maxImageDimension = 16_384;
    private long maxImageBytes = 50L * 1024L * 1024L;
    private long maxTotalImageBytes = 512L * 1024L * 1024L;
    private long maxArchiveBytes = 520L * 1024L * 1024L;
    private int maxPageTreeNodes = 10_000;
    private int maxPageTreeDepth = 32;
    private int maxContentStreamsPerPage = 1000;
    private long maxWorkerHeapBytes = 256L * 1024L * 1024L;
    private Duration workerTimeout = Duration.ofMinutes(5);

    public int getMaxDocumentPages() {
        return maxDocumentPages;
    }

    public void setMaxDocumentPages(int maxDocumentPages) {
        this.maxDocumentPages = maxDocumentPages;
    }

    public int getMaxSelectedPages() {
        return maxSelectedPages;
    }

    public void setMaxSelectedPages(int maxSelectedPages) {
        this.maxSelectedPages = maxSelectedPages;
    }

    public int getMinDpi() {
        return minDpi;
    }

    public void setMinDpi(int minDpi) {
        this.minDpi = minDpi;
    }

    public int getMaxDpi() {
        return maxDpi;
    }

    public void setMaxDpi(int maxDpi) {
        this.maxDpi = maxDpi;
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

    public long getMaxArchiveBytes() {
        return maxArchiveBytes;
    }

    public void setMaxArchiveBytes(long maxArchiveBytes) {
        this.maxArchiveBytes = maxArchiveBytes;
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

    public long getMaxWorkerHeapBytes() {
        return maxWorkerHeapBytes;
    }

    public void setMaxWorkerHeapBytes(long maxWorkerHeapBytes) {
        this.maxWorkerHeapBytes = maxWorkerHeapBytes;
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
