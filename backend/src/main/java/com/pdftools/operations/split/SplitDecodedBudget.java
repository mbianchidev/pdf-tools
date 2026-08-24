package com.pdftools.operations.split;

import com.pdftools.operations.OperationException;

final class SplitDecodedBudget {

    private long remainingBytes;
    private final String errorCode;
    private final String errorMessage;

    SplitDecodedBudget(long maxBytes) {
        this(
            maxBytes,
            "SPLIT_DECODED_CONTENT_LIMIT_EXCEEDED",
            "Split decoded content exceeds the configured total limit"
        );
    }

    SplitDecodedBudget(
            long maxBytes,
            String errorCode,
            String errorMessage) {
        this.remainingBytes = maxBytes;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    long remainingBytes() {
        return remainingBytes;
    }

    void consume(long bytes) {
        if (bytes < 0 || bytes > remainingBytes) {
            throw new OperationException(
                errorCode,
                errorMessage
            );
        }
        remainingBytes -= bytes;
    }
}
