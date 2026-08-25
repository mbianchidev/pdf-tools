package com.pdftools.operations.repair;

import com.pdftools.operations.shared.pdf.PdfPageTreeLimits;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "pdf.operations.repair")
public class RepairPdfProperties implements PdfPageTreeLimits {

    private String qpdfBinary = "qpdf";
    private long maxInputBytes = 100L * 1024L * 1024L;
    private long maxOutputBytes = 128L * 1024L * 1024L;
    private long maxLogBytes = 1024L * 1024L;
    private long maxReportBytes = 128L * 1024L;
    private int maxWarnings = 100;
    private int maxWarningCharacters = 500;
    private long maxAddressSpaceBytes = 1024L * 1024L * 1024L;
    private int cpuTimeSeconds = 120;
    private int maxOpenFiles = 128;
    private boolean allowUnsandboxedLinux;
    private Duration rewriteTimeout = Duration.ofMinutes(2);
    private Duration checkTimeout = Duration.ofMinutes(1);
    private int maxPages = 1_000;
    private int maxPageTreeNodes = 10_000;
    private int maxPageTreeDepth = 64;
    private int maxContentStreamsPerPage = 1_000;

    public String getQpdfBinary() {
        return qpdfBinary;
    }

    public void setQpdfBinary(String value) {
        qpdfBinary = value;
    }

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

    public long getMaxLogBytes() {
        return maxLogBytes;
    }

    public void setMaxLogBytes(long value) {
        maxLogBytes = value;
    }

    public long getMaxReportBytes() {
        return maxReportBytes;
    }

    public void setMaxReportBytes(long value) {
        maxReportBytes = value;
    }

    public int getMaxWarnings() {
        return maxWarnings;
    }

    public void setMaxWarnings(int value) {
        maxWarnings = value;
    }

    public int getMaxWarningCharacters() {
        return maxWarningCharacters;
    }

    public void setMaxWarningCharacters(int value) {
        maxWarningCharacters = value;
    }

    public long getMaxAddressSpaceBytes() {
        return maxAddressSpaceBytes;
    }

    public void setMaxAddressSpaceBytes(long value) {
        maxAddressSpaceBytes = value;
    }

    public int getCpuTimeSeconds() {
        return cpuTimeSeconds;
    }

    public void setCpuTimeSeconds(int value) {
        cpuTimeSeconds = value;
    }

    public int getMaxOpenFiles() {
        return maxOpenFiles;
    }

    public void setMaxOpenFiles(int value) {
        maxOpenFiles = value;
    }

    public boolean isAllowUnsandboxedLinux() {
        return allowUnsandboxedLinux;
    }

    public void setAllowUnsandboxedLinux(boolean value) {
        allowUnsandboxedLinux = value;
    }

    public Duration getRewriteTimeout() {
        return rewriteTimeout;
    }

    public void setRewriteTimeout(Duration value) {
        rewriteTimeout = value;
    }

    public Duration getCheckTimeout() {
        return checkTimeout;
    }

    public void setCheckTimeout(Duration value) {
        checkTimeout = value;
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
