package com.pdftools.operations.pdfexcel;

import com.pdftools.operations.shared.pdf.PdfPageTreeLimits;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "pdf.operations.pdf-excel")
public class PdfToExcelProperties implements PdfPageTreeLimits {

    private long maxInputBytes = 50L * 1024L * 1024L;
    private int maxPages = 200;
    private int maxTextCharacters = 2_000_000;
    private int maxTables = 200;
    private int maxSheets = 200;
    private int maxRowsPerSheet = 100_000;
    private int maxColumns = 100;
    private long maxCells = 1_000_000;
    private long maxOutputBytes = 100L * 1024L * 1024L;
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

    public int getMaxTables() {
        return maxTables;
    }

    public void setMaxTables(int value) {
        maxTables = value;
    }

    public int getMaxSheets() {
        return maxSheets;
    }

    public void setMaxSheets(int value) {
        maxSheets = value;
    }

    public int getMaxRowsPerSheet() {
        return maxRowsPerSheet;
    }

    public void setMaxRowsPerSheet(int value) {
        maxRowsPerSheet = value;
    }

    public int getMaxColumns() {
        return maxColumns;
    }

    public void setMaxColumns(int value) {
        maxColumns = value;
    }

    public long getMaxCells() {
        return maxCells;
    }

    public void setMaxCells(long value) {
        maxCells = value;
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
