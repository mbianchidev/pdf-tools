package com.pdftools.operations.pdfword;

import com.pdftools.operations.shared.pdf.PdfPageTreeLimits;
import com.pdftools.operations.shared.extraction.PdfPageRasterizationLimits;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "pdf.operations.pdf-word")
public class PdfToWordProperties
        implements PdfPageTreeLimits, PdfPageRasterizationLimits {

    private long maxInputBytes = 50L * 1024L * 1024L;
    private int maxPages = 200;
    private int maxTextCharacters = 2_000_000;
    private int maxImages = 200;
    private long maxPixelsPerImage = 20_000_000;
    private long maxTotalImagePixels = 200_000_000;
    private long maxImageBytes = 25L * 1024L * 1024L;
    private long maxTotalImageBytes = 256L * 1024L * 1024L;
    private long maxOutputBytes = 128L * 1024L * 1024L;
    private int renderDpi = 144;
    private long maxRenderPixelsPerPage = 20_000_000;
    private int maxImageDimension = 16_384;
    private int maxTableColumns = 12;
    private long workerHeapBytes = 512L * 1024L * 1024L;
    private Duration workerTimeout = Duration.ofMinutes(5);
    private int maxPageTreeNodes = 10_000;
    private int maxPageTreeDepth = 64;
    private int maxContentStreamsPerPage = 32;

    public long getMaxInputBytes() {
        return maxInputBytes;
    }

    public void setMaxInputBytes(long maxInputBytes) {
        this.maxInputBytes = maxInputBytes;
    }

    public int getMaxPages() {
        return maxPages;
    }

    public void setMaxPages(int maxPages) {
        this.maxPages = maxPages;
    }

    @Override
    public int maxPages() {
        return maxPages;
    }

    public int getMaxTextCharacters() {
        return maxTextCharacters;
    }

    public void setMaxTextCharacters(int maxTextCharacters) {
        this.maxTextCharacters = maxTextCharacters;
    }

    public int getMaxImages() {
        return maxImages;
    }

    public void setMaxImages(int maxImages) {
        this.maxImages = maxImages;
    }

    public long getMaxPixelsPerImage() {
        return maxPixelsPerImage;
    }

    public void setMaxPixelsPerImage(long maxPixelsPerImage) {
        this.maxPixelsPerImage = maxPixelsPerImage;
    }

    public long getMaxTotalImagePixels() {
        return maxTotalImagePixels;
    }

    public void setMaxTotalImagePixels(long maxTotalImagePixels) {
        this.maxTotalImagePixels = maxTotalImagePixels;
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

    public int getRenderDpi() {
        return renderDpi;
    }

    public void setRenderDpi(int renderDpi) {
        this.renderDpi = renderDpi;
    }

    public long getMaxRenderPixelsPerPage() {
        return maxRenderPixelsPerPage;
    }

    public void setMaxRenderPixelsPerPage(long maxRenderPixelsPerPage) {
        this.maxRenderPixelsPerPage = maxRenderPixelsPerPage;
    }

    public int getMaxImageDimension() {
        return maxImageDimension;
    }

    public void setMaxImageDimension(int maxImageDimension) {
        this.maxImageDimension = maxImageDimension;
    }

    public int getMaxTableColumns() {
        return maxTableColumns;
    }

    public void setMaxTableColumns(int maxTableColumns) {
        this.maxTableColumns = maxTableColumns;
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

    public int getMaxPageTreeNodes() {
        return maxPageTreeNodes;
    }

    public void setMaxPageTreeNodes(int maxPageTreeNodes) {
        this.maxPageTreeNodes = maxPageTreeNodes;
    }

    @Override
    public int maxPageTreeNodes() {
        return maxPageTreeNodes;
    }

    public int getMaxPageTreeDepth() {
        return maxPageTreeDepth;
    }

    public void setMaxPageTreeDepth(int maxPageTreeDepth) {
        this.maxPageTreeDepth = maxPageTreeDepth;
    }

    @Override
    public int maxPageTreeDepth() {
        return maxPageTreeDepth;
    }

    public int getMaxContentStreamsPerPage() {
        return maxContentStreamsPerPage;
    }

    public void setMaxContentStreamsPerPage(
            int maxContentStreamsPerPage) {
        this.maxContentStreamsPerPage = maxContentStreamsPerPage;
    }

    @Override
    public int maxContentStreamsPerPage() {
        return maxContentStreamsPerPage;
    }
}
