package com.pdftools.operations;

import java.nio.file.Path;

public record OperationOutput(
    Path path,
    String filename,
    String mediaType
) {
}
