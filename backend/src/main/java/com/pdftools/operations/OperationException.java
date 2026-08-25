package com.pdftools.operations;

import java.util.Map;

public class OperationException extends RuntimeException {

    private final String code;
    private final Map<String, Object> details;

    public OperationException(String code, String message) {
        this(code, message, Map.of());
    }

    public OperationException(String code, String message, Map<String, Object> details) {
        super(message);
        this.code = code;
        this.details = details == null ? Map.of() : Map.copyOf(details);
    }

    public OperationException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.details = Map.of();
    }

    public String getCode() {
        return code;
    }

    public Map<String, Object> getDetails() {
        return details;
    }
}
