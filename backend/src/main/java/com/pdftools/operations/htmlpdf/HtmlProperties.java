package com.pdftools.operations.htmlpdf;

import com.pdftools.operations.shared.queue.ConversionQueueProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.time.Duration;

@ConfigurationProperties(prefix = "pdf.operations.html")
public class HtmlProperties implements ConversionQueueProperties {

    private Path queueRequestRoot =
        Path.of("/var/lib/pdf-tools-html/requests");
    private Path queueResponseRoot =
        Path.of("/var/lib/pdf-tools-html/responses");
    private Path queueSignalRoot =
        Path.of("/var/lib/pdf-tools-html/signals");
    private Duration queueWaitTimeout = Duration.ofMinutes(5);
    private Duration queueRetention = Duration.ofHours(1);
    private Duration wallTimeout = Duration.ofMinutes(1);
    private long maxInputBytes = 10L * 1024L * 1024L;
    private long maxOutputBytes = 64L * 1024L * 1024L;
    private int maxPages = 200;

    @Override
    public String getQueueCodePrefix() {
        return "HTML";
    }

    @Override
    public String getQueueLabel() {
        return "HTML";
    }

    @Override
    public String getMode() {
        return "queue";
    }

    @Override
    public Path getQueueRequestRoot() {
        return queueRequestRoot;
    }

    public void setQueueRequestRoot(Path queueRequestRoot) {
        this.queueRequestRoot = queueRequestRoot;
    }

    @Override
    public Path getQueueResponseRoot() {
        return queueResponseRoot;
    }

    public void setQueueResponseRoot(Path queueResponseRoot) {
        this.queueResponseRoot = queueResponseRoot;
    }

    @Override
    public Path getQueueSignalRoot() {
        return queueSignalRoot;
    }

    public void setQueueSignalRoot(Path queueSignalRoot) {
        this.queueSignalRoot = queueSignalRoot;
    }

    @Override
    public Duration getQueueWaitTimeout() {
        return queueWaitTimeout;
    }

    public void setQueueWaitTimeout(Duration queueWaitTimeout) {
        this.queueWaitTimeout = queueWaitTimeout;
    }

    @Override
    public Duration getQueueRetention() {
        return queueRetention;
    }

    public void setQueueRetention(Duration queueRetention) {
        this.queueRetention = queueRetention;
    }

    @Override
    public Duration getWallTimeout() {
        return wallTimeout;
    }

    public void setWallTimeout(Duration wallTimeout) {
        this.wallTimeout = wallTimeout;
    }

    public long getMaxInputBytes() {
        return maxInputBytes;
    }

    public void setMaxInputBytes(long maxInputBytes) {
        this.maxInputBytes = maxInputBytes;
    }

    @Override
    public long getMaxOutputBytes() {
        return maxOutputBytes;
    }

    public void setMaxOutputBytes(long maxOutputBytes) {
        this.maxOutputBytes = maxOutputBytes;
    }

    public int getMaxPages() {
        return maxPages;
    }

    public void setMaxPages(int maxPages) {
        this.maxPages = maxPages;
    }
}
