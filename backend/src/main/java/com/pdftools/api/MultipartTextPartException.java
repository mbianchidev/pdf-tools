package com.pdftools.api;

import org.springframework.http.HttpStatus;

public class MultipartTextPartException extends RuntimeException {

    public enum Reason {
        MISSING,
        TOO_LARGE,
        INVALID_UTF8,
        UNREADABLE
    }

    private final HttpStatus status;
    private final Reason reason;
    private final String partName;

    public MultipartTextPartException(
            HttpStatus status,
            Reason reason,
            String partName,
            String message,
            Throwable cause) {
        super(message, cause);
        this.status = status;
        this.reason = reason;
        this.partName = partName;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public Reason getReason() {
        return reason;
    }

    public String getPartName() {
        return partName;
    }
}
