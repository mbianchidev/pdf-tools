package com.pdftools.operations;

import tools.jackson.databind.JsonNode;

import java.util.List;

public record OperationSubmission(
    JsonNode options,
    List<UploadDescriptor> files
) {
    public OperationSubmission {
        files = List.copyOf(files);
    }

    public record UploadDescriptor(
        int position,
        String filename,
        String mediaType,
        long sizeBytes
    ) {
    }
}
