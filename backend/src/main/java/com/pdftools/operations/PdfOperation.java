package com.pdftools.operations;

import java.util.List;

public interface PdfOperation {

    String key();

    default void validateSubmission(OperationSubmission submission) {
    }

    List<OperationOutput> execute(OperationContext context);
}
