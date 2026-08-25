package com.pdftools.operations.pdfa;

import com.pdftools.operations.shared.pdf.PdfPageTreeLimits;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "pdf.operations.pdfa")
public class PdfAProperties implements PdfPageTreeLimits {

    private long maxInputBytes = 50L * 1024L * 1024L;
    private long maxOutputBytes = 128L * 1024L * 1024L;
    private int maxPages = 200;
    private int maxRuleFailures = 100;
    private int maxFailureCharacters = 500;
    private long maxReportBytes = 256L * 1024L;
    private long validatorHeapBytes = 512L * 1024L * 1024L;
    private Duration validatorTimeout = Duration.ofMinutes(5);
    private int maxPageTreeNodes = 10_000;
    private int maxPageTreeDepth = 64;
    private int maxContentStreamsPerPage = 1_000;

    public long getMaxInputBytes() {
        return maxInputBytes;
    }

    public void setMaxInputBytes(long value) {
        maxInputBytes = value;
    }

    public long getMaxOutputBytes() {
        return maxOutputBytes;
    }

    public void setMaxOutputBytes(long value) {
        maxOutputBytes = value;
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

    public int getMaxRuleFailures() {
        return maxRuleFailures;
    }

    public void setMaxRuleFailures(int value) {
        maxRuleFailures = value;
    }

    public int getMaxFailureCharacters() {
        return maxFailureCharacters;
    }

    public void setMaxFailureCharacters(int value) {
        maxFailureCharacters = value;
    }

    public long getMaxReportBytes() {
        return maxReportBytes;
    }

    public void setMaxReportBytes(long value) {
        maxReportBytes = value;
    }

    public long getValidatorHeapBytes() {
        return validatorHeapBytes;
    }

    public void setValidatorHeapBytes(long value) {
        validatorHeapBytes = value;
    }

    public Duration getValidatorTimeout() {
        return validatorTimeout;
    }

    public void setValidatorTimeout(Duration value) {
        validatorTimeout = value;
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
