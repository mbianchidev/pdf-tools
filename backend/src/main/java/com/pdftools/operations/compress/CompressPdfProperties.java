package com.pdftools.operations.compress;

import com.pdftools.operations.shared.pdf.PdfPageTreeLimits;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "pdf.operations.compress")
public class CompressPdfProperties implements PdfPageTreeLimits {

    private long maxInputBytes = 100L * 1024L * 1024L;
    private int maxPages = 500;
    private int maxImages = 500;
    private long maxPixelsPerImage = 20_000_000;
    private long maxTotalImagePixels = 200_000_000;
    private int maxImageDimension = 8_192;
    private int recommendedMaxImageDimension = 2_400;
    private int recommendedJpegQuality = 82;
    private int extremeMaxImageDimension = 1_400;
    private int extremeJpegQuality = 60;
    private long maxTemporaryImageBytes = 32L * 1024L * 1024L;
    private long maxTotalRecompressedImageBytes =
        256L * 1024L * 1024L;
    private int maxXObjects = 10_000;
    private int maxResourceDepth = 32;
    private long maxOutputBytes = 128L * 1024L * 1024L;
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

    public int getMaxImages() {
        return maxImages;
    }

    public void setMaxImages(int value) {
        maxImages = value;
    }

    public long getMaxPixelsPerImage() {
        return maxPixelsPerImage;
    }

    public void setMaxPixelsPerImage(long value) {
        maxPixelsPerImage = value;
    }

    public long getMaxTotalImagePixels() {
        return maxTotalImagePixels;
    }

    public void setMaxTotalImagePixels(long value) {
        maxTotalImagePixels = value;
    }

    public int getMaxImageDimension() {
        return maxImageDimension;
    }

    public void setMaxImageDimension(int value) {
        maxImageDimension = value;
    }

    public int getRecommendedMaxImageDimension() {
        return recommendedMaxImageDimension;
    }

    public void setRecommendedMaxImageDimension(int value) {
        recommendedMaxImageDimension = value;
    }

    public int getRecommendedJpegQuality() {
        return recommendedJpegQuality;
    }

    public void setRecommendedJpegQuality(int value) {
        recommendedJpegQuality = value;
    }

    public int getExtremeMaxImageDimension() {
        return extremeMaxImageDimension;
    }

    public void setExtremeMaxImageDimension(int value) {
        extremeMaxImageDimension = value;
    }

    public int getExtremeJpegQuality() {
        return extremeJpegQuality;
    }

    public void setExtremeJpegQuality(int value) {
        extremeJpegQuality = value;
    }

    public long getMaxTemporaryImageBytes() {
        return maxTemporaryImageBytes;
    }

    public void setMaxTemporaryImageBytes(long value) {
        maxTemporaryImageBytes = value;
    }

    public long getMaxTotalRecompressedImageBytes() {
        return maxTotalRecompressedImageBytes;
    }

    public void setMaxTotalRecompressedImageBytes(long value) {
        maxTotalRecompressedImageBytes = value;
    }

    public int getMaxXObjects() {
        return maxXObjects;
    }

    public void setMaxXObjects(int value) {
        maxXObjects = value;
    }

    public int getMaxResourceDepth() {
        return maxResourceDepth;
    }

    public void setMaxResourceDepth(int value) {
        maxResourceDepth = value;
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
