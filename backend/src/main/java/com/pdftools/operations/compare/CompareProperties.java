package com.pdftools.operations.compare;

import com.pdftools.operations.shared.pdf.PdfPageTreeLimits;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "pdf.operations.compare")
public class CompareProperties implements PdfPageTreeLimits {

    private long maxInputBytes = 50L * 1024L * 1024L;
    private long maxTotalInputBytes = 100L * 1024L * 1024L;
    private int maxPages = 200;
    private int maxTextCharactersPerDocument = 2_000_000;
    private int maxTextLinesPerPage = 1_000;
    private int maxLineCharacters = 1_000;
    private long maxDiffMatrixCells = 1_000_000;
    private int maxTextChanges = 5_000;
    private long maxPixelsPerPage = 10_000_000;
    private long maxTotalRenderPixels = 200_000_000;
    private int maxImageDimension = 8_192;
    private long maxDiffImageBytes = 25L * 1024L * 1024L;
    private long maxTotalDiffImageBytes = 256L * 1024L * 1024L;
    private long maxReportBytes = 4L * 1024L * 1024L;
    private long maxArchiveBytes = 300L * 1024L * 1024L;
    private long workerHeapBytes = 512L * 1024L * 1024L;
    private Duration workerTimeout = Duration.ofMinutes(5);
    private int maxPageTreeNodes = 10_000;
    private int maxPageTreeDepth = 64;
    private int maxContentStreamsPerPage = 1_000;

    public long getMaxInputBytes() {
        return maxInputBytes;
    }

    public void setMaxInputBytes(long value) {
        maxInputBytes = value;
    }

    public long getMaxTotalInputBytes() {
        return maxTotalInputBytes;
    }

    public void setMaxTotalInputBytes(long value) {
        maxTotalInputBytes = value;
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

    public int getMaxTextCharactersPerDocument() {
        return maxTextCharactersPerDocument;
    }

    public void setMaxTextCharactersPerDocument(int value) {
        maxTextCharactersPerDocument = value;
    }

    public int getMaxTextLinesPerPage() {
        return maxTextLinesPerPage;
    }

    public void setMaxTextLinesPerPage(int value) {
        maxTextLinesPerPage = value;
    }

    public int getMaxLineCharacters() {
        return maxLineCharacters;
    }

    public void setMaxLineCharacters(int value) {
        maxLineCharacters = value;
    }

    public long getMaxDiffMatrixCells() {
        return maxDiffMatrixCells;
    }

    public void setMaxDiffMatrixCells(long value) {
        maxDiffMatrixCells = value;
    }

    public int getMaxTextChanges() {
        return maxTextChanges;
    }

    public void setMaxTextChanges(int value) {
        maxTextChanges = value;
    }

    public long getMaxPixelsPerPage() {
        return maxPixelsPerPage;
    }

    public void setMaxPixelsPerPage(long value) {
        maxPixelsPerPage = value;
    }

    public long getMaxTotalRenderPixels() {
        return maxTotalRenderPixels;
    }

    public void setMaxTotalRenderPixels(long value) {
        maxTotalRenderPixels = value;
    }

    public int getMaxImageDimension() {
        return maxImageDimension;
    }

    public void setMaxImageDimension(int value) {
        maxImageDimension = value;
    }

    public long getMaxDiffImageBytes() {
        return maxDiffImageBytes;
    }

    public void setMaxDiffImageBytes(long value) {
        maxDiffImageBytes = value;
    }

    public long getMaxTotalDiffImageBytes() {
        return maxTotalDiffImageBytes;
    }

    public void setMaxTotalDiffImageBytes(long value) {
        maxTotalDiffImageBytes = value;
    }

    public long getMaxReportBytes() {
        return maxReportBytes;
    }

    public void setMaxReportBytes(long value) {
        maxReportBytes = value;
    }

    public long getMaxArchiveBytes() {
        return maxArchiveBytes;
    }

    public void setMaxArchiveBytes(long value) {
        maxArchiveBytes = value;
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
