package com.pdftools.operations.office;

import com.pdftools.operations.shared.queue.ConversionQueueProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.time.Duration;

@ConfigurationProperties(prefix = "pdf.operations.office")
public class OfficeConversionProperties implements ConversionQueueProperties {

    private String mode = "queue";
    private Path queueRequestRoot =
        Path.of("/var/lib/pdf-tools-office/requests");
    private Path queueResponseRoot =
        Path.of("/var/lib/pdf-tools-office/responses");
    private Path queueSignalRoot =
        Path.of("/var/lib/pdf-tools-office/signals");
    private Duration queueWaitTimeout = Duration.ofMinutes(5);
    private Duration queueRetention = Duration.ofHours(1);
    private Path sidecarWorkRoot = Path.of("/tmp/office-work");
    private String workerUser = "officeworker";
    private String libreOfficeBinary = "soffice";
    private long maxInputBytes = 50L * 1024L * 1024L;
    private long maxExpandedInputBytes = 256L * 1024L * 1024L;
    private int maxArchiveEntries = 10_000;
    private long maxOutputBytes = 128L * 1024L * 1024L;
    private long maxLogBytes = 1024L * 1024L;
    private long maxAddressSpaceBytes = 1024L * 1024L * 1024L;
    private long cpuTimeSeconds = 120;
    private int maxOpenFiles = 256;
    private int maxWorkerProcesses = 96;
    private Duration wallTimeout = Duration.ofMinutes(2);
    private boolean isolatedContainer;

    @Override
    public String getQueueCodePrefix() {
        return "OFFICE";
    }

    @Override
    public String getQueueLabel() {
        return "Office";
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public Path getQueueRequestRoot() {
        return queueRequestRoot;
    }

    public void setQueueRequestRoot(Path queueRequestRoot) {
        this.queueRequestRoot = queueRequestRoot;
    }

    public Path getQueueResponseRoot() {
        return queueResponseRoot;
    }

    public void setQueueResponseRoot(Path queueResponseRoot) {
        this.queueResponseRoot = queueResponseRoot;
    }

    public Path getQueueSignalRoot() {
        return queueSignalRoot;
    }

    public void setQueueSignalRoot(Path queueSignalRoot) {
        this.queueSignalRoot = queueSignalRoot;
    }

    public Duration getQueueWaitTimeout() {
        return queueWaitTimeout;
    }

    public void setQueueWaitTimeout(Duration queueWaitTimeout) {
        this.queueWaitTimeout = queueWaitTimeout;
    }

    public Duration getQueueRetention() {
        return queueRetention;
    }

    public void setQueueRetention(Duration queueRetention) {
        this.queueRetention = queueRetention;
    }

    public Path getSidecarWorkRoot() {
        return sidecarWorkRoot;
    }

    public void setSidecarWorkRoot(Path sidecarWorkRoot) {
        this.sidecarWorkRoot = sidecarWorkRoot;
    }

    public String getWorkerUser() {
        return workerUser;
    }

    public void setWorkerUser(String workerUser) {
        this.workerUser = workerUser;
    }

    public String getLibreOfficeBinary() {
        return libreOfficeBinary;
    }

    public void setLibreOfficeBinary(String libreOfficeBinary) {
        this.libreOfficeBinary = libreOfficeBinary;
    }

    public long getMaxInputBytes() {
        return maxInputBytes;
    }

    public void setMaxInputBytes(long maxInputBytes) {
        this.maxInputBytes = maxInputBytes;
    }

    public long getMaxExpandedInputBytes() {
        return maxExpandedInputBytes;
    }

    public void setMaxExpandedInputBytes(long maxExpandedInputBytes) {
        this.maxExpandedInputBytes = maxExpandedInputBytes;
    }

    public int getMaxArchiveEntries() {
        return maxArchiveEntries;
    }

    public void setMaxArchiveEntries(int maxArchiveEntries) {
        this.maxArchiveEntries = maxArchiveEntries;
    }

    public long getMaxOutputBytes() {
        return maxOutputBytes;
    }

    public void setMaxOutputBytes(long maxOutputBytes) {
        this.maxOutputBytes = maxOutputBytes;
    }

    public long getMaxLogBytes() {
        return maxLogBytes;
    }

    public void setMaxLogBytes(long maxLogBytes) {
        this.maxLogBytes = maxLogBytes;
    }

    public long getMaxAddressSpaceBytes() {
        return maxAddressSpaceBytes;
    }

    public void setMaxAddressSpaceBytes(long maxAddressSpaceBytes) {
        this.maxAddressSpaceBytes = maxAddressSpaceBytes;
    }

    public long getCpuTimeSeconds() {
        return cpuTimeSeconds;
    }

    public void setCpuTimeSeconds(long cpuTimeSeconds) {
        this.cpuTimeSeconds = cpuTimeSeconds;
    }

    public int getMaxOpenFiles() {
        return maxOpenFiles;
    }

    public void setMaxOpenFiles(int maxOpenFiles) {
        this.maxOpenFiles = maxOpenFiles;
    }

    public int getMaxWorkerProcesses() {
        return maxWorkerProcesses;
    }

    public void setMaxWorkerProcesses(int maxWorkerProcesses) {
        this.maxWorkerProcesses = maxWorkerProcesses;
    }

    public Duration getWallTimeout() {
        return wallTimeout;
    }

    public void setWallTimeout(Duration wallTimeout) {
        this.wallTimeout = wallTimeout;
    }

    public boolean isIsolatedContainer() {
        return isolatedContainer;
    }

    public void setIsolatedContainer(boolean isolatedContainer) {
        this.isolatedContainer = isolatedContainer;
    }
}
