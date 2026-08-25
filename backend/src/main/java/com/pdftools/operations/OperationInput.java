package com.pdftools.operations;

import java.nio.file.Path;

public record OperationInput(
    int position,
    Path path,
    String originalFilename,
    String mediaType,
    long sizeBytes,
    String sha256
) {
}
