package com.pdftools.operations.pdfjpg;

import java.nio.file.Path;
import java.util.List;

public record PdfToJpgResult(List<Part> parts) {

    public PdfToJpgResult {
        parts = List.copyOf(parts);
    }

    public record Part(Path path, int pageNumber) {
    }
}
