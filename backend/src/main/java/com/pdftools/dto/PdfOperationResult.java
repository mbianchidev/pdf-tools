package com.pdftools.dto;

import java.util.Objects;

public class PdfOperationResult {
    private boolean success;
    private String message;
    private String outputFilename;

    public PdfOperationResult() {
    }

    public PdfOperationResult(boolean success, String message, String outputFilename) {
        this.success = success;
        this.message = message;
        this.outputFilename = outputFilename;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getOutputFilename() {
        return outputFilename;
    }

    public void setOutputFilename(String outputFilename) {
        this.outputFilename = outputFilename;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PdfOperationResult that = (PdfOperationResult) o;
        return success == that.success
                && Objects.equals(message, that.message)
                && Objects.equals(outputFilename, that.outputFilename);
    }

    @Override
    public int hashCode() {
        return Objects.hash(success, message, outputFilename);
    }

    @Override
    public String toString() {
        return "PdfOperationResult("
                + "success=" + success
                + ", message=" + message
                + ", outputFilename=" + outputFilename
                + ")";
    }
}
