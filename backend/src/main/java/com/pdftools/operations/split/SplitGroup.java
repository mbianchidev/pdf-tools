package com.pdftools.operations.split;

import java.util.List;

public record SplitGroup(
    int position,
    List<Integer> pages
) {
    public SplitGroup {
        pages = List.copyOf(pages);
    }
}
