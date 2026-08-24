package com.pdftools.operations.split;

import java.nio.file.Path;
import java.util.List;

public record SplitResult(
    List<Part> parts
) {
    public SplitResult {
        parts = List.copyOf(parts);
    }

    public record Part(
        int position,
        List<Integer> pages,
        Path path
    ) {
        public Part {
            pages = List.copyOf(pages);
        }
    }
}
