package com.pdftools.operations.merge;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pdf.operations.merge")
public class MergeProperties {

    private int maxFiles = 20;
    private long maxTotalInputBytes = 100L * 1024L * 1024L;
    private int maxPagesPerFile = 10_000;
    private int maxTotalPages = 20_000;

    public int getMaxFiles() {
        return maxFiles;
    }

    public void setMaxFiles(int maxFiles) {
        this.maxFiles = maxFiles;
    }

    public long getMaxTotalInputBytes() {
        return maxTotalInputBytes;
    }

    public void setMaxTotalInputBytes(long maxTotalInputBytes) {
        this.maxTotalInputBytes = maxTotalInputBytes;
    }

    public int getMaxPagesPerFile() {
        return maxPagesPerFile;
    }

    public void setMaxPagesPerFile(int maxPagesPerFile) {
        this.maxPagesPerFile = maxPagesPerFile;
    }

    public int getMaxTotalPages() {
        return maxTotalPages;
    }

    public void setMaxTotalPages(int maxTotalPages) {
        this.maxTotalPages = maxTotalPages;
    }
}
