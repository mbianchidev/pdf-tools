package com.pdftools.operations.excelpdf;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.Duration;

@ConfigurationProperties(prefix = "pdf.operations.excel")
public class ExcelProperties {

    private int maxSheets = 100;
    private long maxUsedCells = 1_000_000;
    private long maxPreparedBytes = 100L * 1024L * 1024L;
    private long workerHeapBytes = 512L * 1024L * 1024L;
    private long workerAddressSpaceBytes = 2L * 1024L * 1024L * 1024L;
    private Duration workerTimeout = Duration.ofMinutes(2);

    public int getMaxSheets() {
        return maxSheets;
    }

    public void setMaxSheets(int maxSheets) {
        this.maxSheets = maxSheets;
    }

    public long getMaxUsedCells() {
        return maxUsedCells;
    }

    public void setMaxUsedCells(long maxUsedCells) {
        this.maxUsedCells = maxUsedCells;
    }

    public long getMaxPreparedBytes() {
        return maxPreparedBytes;
    }

    public void setMaxPreparedBytes(long maxPreparedBytes) {
        this.maxPreparedBytes = maxPreparedBytes;
    }

    public long getWorkerHeapBytes() {
        return workerHeapBytes;
    }

    public void setWorkerHeapBytes(long workerHeapBytes) {
        this.workerHeapBytes = workerHeapBytes;
    }

    public long getWorkerAddressSpaceBytes() {
        return workerAddressSpaceBytes;
    }

    public void setWorkerAddressSpaceBytes(long workerAddressSpaceBytes) {
        this.workerAddressSpaceBytes = workerAddressSpaceBytes;
    }

    public Duration getWorkerTimeout() {
        return workerTimeout;
    }

    public void setWorkerTimeout(Duration workerTimeout) {
        this.workerTimeout = workerTimeout;
    }
}
