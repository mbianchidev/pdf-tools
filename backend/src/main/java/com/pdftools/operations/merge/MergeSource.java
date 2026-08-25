package com.pdftools.operations.merge;

import java.nio.file.Path;

public record MergeSource(
    int position,
    Path path,
    String filename,
    long sizeBytes
) {
}
