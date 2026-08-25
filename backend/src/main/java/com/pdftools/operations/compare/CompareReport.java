package com.pdftools.operations.compare;

import java.util.List;

record CompareReport(
    String status,
    Summary summary,
    List<PageComparison> pages
) {
    record Summary(
        int baselinePages,
        int candidatePages,
        int comparedPages,
        int textChangedPages,
        int layoutChangedPages,
        int visualChangedPages,
        int totalAddedLines,
        int totalRemovedLines,
        double maxVisualDifferencePercent
    ) {
    }

    record PageComparison(
        int page,
        boolean baselinePresent,
        boolean candidatePresent,
        TextComparison text,
        LayoutComparison layout,
        VisualComparison visual
    ) {
        boolean changed() {
            return text.changed()
                || layout.changed()
                || visual.changed();
        }
    }

    record TextComparison(
        boolean changed,
        int baselineLines,
        int candidateLines,
        int addedLines,
        int removedLines,
        List<TextChange> changes
    ) {
    }

    record TextChange(
        String type,
        Integer baselineLine,
        Integer candidateLine,
        String text
    ) {
    }

    record LayoutComparison(
        boolean changed,
        boolean pageGeometryChanged,
        int movedTextLines,
        PageGeometry baseline,
        PageGeometry candidate
    ) {
    }

    record PageGeometry(
        double widthPoints,
        double heightPoints,
        int rotation
    ) {
    }

    record VisualComparison(
        boolean changed,
        long differentPixels,
        long totalPixels,
        double differencePercent,
        String diffImage
    ) {
    }
}
