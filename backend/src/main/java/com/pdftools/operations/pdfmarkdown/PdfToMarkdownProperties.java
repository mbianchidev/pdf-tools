package com.pdftools.operations.pdfmarkdown;

import com.pdftools.operations.shared.extraction.PdfImageExtractionLimits;
import com.pdftools.operations.shared.pdf.PdfPageTreeLimits;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "pdf.operations.pdf-markdown")
public class PdfToMarkdownProperties
        implements PdfPageTreeLimits, PdfImageExtractionLimits {

    private long maxInputBytes = 50L * 1024L * 1024L;
    private int maxPages = 200;
    private int maxTextCharacters = 2_000_000;
    private int maxMarkdownCharacters = 4_000_000;
    private int maxTables = 500;
    private int maxTableColumns = 12;
    private int maxImages = 200;
    private long maxPixelsPerImage = 20_000_000;
    private long maxTotalImagePixels = 200_000_000;
    private long maxImageBytes = 25L * 1024L * 1024L;
    private long maxTotalImageBytes = 256L * 1024L * 1024L;
    private int maxImageDimension = 8_192;
    private long maxOutputBytes = 300L * 1024L * 1024L;
    private long workerHeapBytes = 512L * 1024L * 1024L;
    private Duration workerTimeout = Duration.ofMinutes(5);
    private int maxPageTreeNodes = 10_000;
    private int maxPageTreeDepth = 64;
    private int maxContentStreamsPerPage = 32;

    public long getMaxInputBytes() {
        return maxInputBytes;
    }

    public void setMaxInputBytes(long value) {
        maxInputBytes = value;
    }

    public int getMaxPages() {
        return maxPages;
    }

    public void setMaxPages(int value) {
        maxPages = value;
    }

    @Override
    public int maxPages() {
        return maxPages;
    }

    public int getMaxTextCharacters() {
        return maxTextCharacters;
    }

    public void setMaxTextCharacters(int value) {
        maxTextCharacters = value;
    }

    public int getMaxMarkdownCharacters() {
        return maxMarkdownCharacters;
    }

    public void setMaxMarkdownCharacters(int value) {
        maxMarkdownCharacters = value;
    }

    public int getMaxTables() {
        return maxTables;
    }

    public void setMaxTables(int value) {
        maxTables = value;
    }

    public int getMaxTableColumns() {
        return maxTableColumns;
    }

    public void setMaxTableColumns(int value) {
        maxTableColumns = value;
    }

    @Override
    public int getMaxImages() {
        return maxImages;
    }

    public void setMaxImages(int value) {
        maxImages = value;
    }

    @Override
    public long getMaxPixelsPerImage() {
        return maxPixelsPerImage;
    }

    public void setMaxPixelsPerImage(long value) {
        maxPixelsPerImage = value;
    }

    @Override
    public long getMaxTotalImagePixels() {
        return maxTotalImagePixels;
    }

    public void setMaxTotalImagePixels(long value) {
        maxTotalImagePixels = value;
    }

    @Override
    public long getMaxImageBytes() {
        return maxImageBytes;
    }

    public void setMaxImageBytes(long value) {
        maxImageBytes = value;
    }

    @Override
    public long getMaxTotalImageBytes() {
        return maxTotalImageBytes;
    }

    public void setMaxTotalImageBytes(long value) {
        maxTotalImageBytes = value;
    }

    @Override
    public int getMaxImageDimension() {
        return maxImageDimension;
    }

    public void setMaxImageDimension(int value) {
        maxImageDimension = value;
    }

    public long getMaxOutputBytes() {
        return maxOutputBytes;
    }

    public void setMaxOutputBytes(long value) {
        maxOutputBytes = value;
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

    public int getMaxPageTreeNodes() {
        return maxPageTreeNodes;
    }

    public void setMaxPageTreeNodes(int value) {
        maxPageTreeNodes = value;
    }

    @Override
    public int maxPageTreeNodes() {
        return maxPageTreeNodes;
    }

    public int getMaxPageTreeDepth() {
        return maxPageTreeDepth;
    }

    public void setMaxPageTreeDepth(int value) {
        maxPageTreeDepth = value;
    }

    @Override
    public int maxPageTreeDepth() {
        return maxPageTreeDepth;
    }

    public int getMaxContentStreamsPerPage() {
        return maxContentStreamsPerPage;
    }

    public void setMaxContentStreamsPerPage(int value) {
        maxContentStreamsPerPage = value;
    }

    @Override
    public int maxContentStreamsPerPage() {
        return maxContentStreamsPerPage;
    }
}
