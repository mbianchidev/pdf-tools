package com.pdftools.operations.split;

import com.pdftools.operations.shared.pdf.PdfPageTreeLimits;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pdf.operations.split")
public class SplitProperties implements PdfPageTreeLimits {

    private int maxPages = 1000;
    private int maxOutputs = 500;
    private int maxFixedGroupSize = 500;
    private long maxOutputBytes = 128L * 1024L * 1024L;
    private long maxTotalOutputBytes = 512L * 1024L * 1024L;
    private long maxDecodedPageBytes = 32L * 1024L * 1024L;
    private long maxTotalDecodedBytes = 256L * 1024L * 1024L;
    private long maxResourceScratchBytes = 512L * 1024L * 1024L;
    private int maxContentTokens = 250_000;
    private int maxContentStreamsPerPage = 1_000;
    private int maxResourceDepth = 32;
    private int maxResourceNodes = 10_000;
    private int maxTotalResourceNodes = 1_000_000;

    public int getMaxPages() {
        return maxPages;
    }

    public void setMaxPages(int maxPages) {
        this.maxPages = maxPages;
    }

    public int getMaxOutputs() {
        return maxOutputs;
    }

    public void setMaxOutputs(int maxOutputs) {
        this.maxOutputs = maxOutputs;
    }

    public int getMaxFixedGroupSize() {
        return maxFixedGroupSize;
    }

    public void setMaxFixedGroupSize(int maxFixedGroupSize) {
        this.maxFixedGroupSize = maxFixedGroupSize;
    }

    public long getMaxOutputBytes() {
        return maxOutputBytes;
    }

    public void setMaxOutputBytes(long maxOutputBytes) {
        this.maxOutputBytes = maxOutputBytes;
    }

    public long getMaxTotalOutputBytes() {
        return maxTotalOutputBytes;
    }

    public void setMaxTotalOutputBytes(long maxTotalOutputBytes) {
        this.maxTotalOutputBytes = maxTotalOutputBytes;
    }

    public long getMaxDecodedPageBytes() {
        return maxDecodedPageBytes;
    }

    public void setMaxDecodedPageBytes(long maxDecodedPageBytes) {
        this.maxDecodedPageBytes = maxDecodedPageBytes;
    }

    public long getMaxTotalDecodedBytes() {
        return maxTotalDecodedBytes;
    }

    public void setMaxTotalDecodedBytes(long maxTotalDecodedBytes) {
        this.maxTotalDecodedBytes = maxTotalDecodedBytes;
    }

    public long getMaxResourceScratchBytes() {
        return maxResourceScratchBytes;
    }

    public void setMaxResourceScratchBytes(long maxResourceScratchBytes) {
        this.maxResourceScratchBytes = maxResourceScratchBytes;
    }

    public int getMaxContentTokens() {
        return maxContentTokens;
    }

    public void setMaxContentTokens(int maxContentTokens) {
        this.maxContentTokens = maxContentTokens;
    }

    public int getMaxContentStreamsPerPage() {
        return maxContentStreamsPerPage;
    }

    public void setMaxContentStreamsPerPage(int maxContentStreamsPerPage) {
        this.maxContentStreamsPerPage = maxContentStreamsPerPage;
    }

    public int getMaxResourceDepth() {
        return maxResourceDepth;
    }

    public void setMaxResourceDepth(int maxResourceDepth) {
        this.maxResourceDepth = maxResourceDepth;
    }

    public int getMaxResourceNodes() {
        return maxResourceNodes;
    }

    public void setMaxResourceNodes(int maxResourceNodes) {
        this.maxResourceNodes = maxResourceNodes;
    }

    public int getMaxTotalResourceNodes() {
        return maxTotalResourceNodes;
    }

    public void setMaxTotalResourceNodes(int maxTotalResourceNodes) {
        this.maxTotalResourceNodes = maxTotalResourceNodes;
    }

    @Override
    public int maxPages() {
        return maxPages;
    }

    @Override
    public int maxPageTreeNodes() {
        return maxResourceNodes;
    }

    @Override
    public int maxPageTreeDepth() {
        return maxResourceDepth;
    }

    @Override
    public int maxContentStreamsPerPage() {
        return maxContentStreamsPerPage;
    }
}
