package com.pdftools.operations;

public class OperationCancelledException extends RuntimeException {

    public OperationCancelledException() {
        super("The PDF job was cancelled");
    }
}
