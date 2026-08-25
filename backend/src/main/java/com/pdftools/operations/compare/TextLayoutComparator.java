package com.pdftools.operations.compare;

import com.pdftools.operations.OperationException;
import com.pdftools.operations.shared.coordinates.VisualPageSpace;
import com.pdftools.operations.shared.extraction.PdfPageContent;

import java.util.ArrayList;
import java.util.List;

final class TextLayoutComparator {

    private final CompareProperties properties;
    private final double layoutTolerancePoints;
    private int totalChanges;

    TextLayoutComparator(
            CompareProperties properties,
            double layoutTolerancePoints) {
        this.properties = properties;
        this.layoutTolerancePoints = layoutTolerancePoints;
    }

    Result compare(
            List<PdfPageContent.TextLine> baselineSource,
            VisualPageSpace baselineSpace,
            List<PdfPageContent.TextLine> candidateSource,
            VisualPageSpace candidateSpace) {
        List<Line> baseline = lines(baselineSource, baselineSpace);
        List<Line> candidate = lines(candidateSource, candidateSpace);
        requireMatrix(baseline.size(), candidate.size());
        int[][] matrix = matrix(baseline, candidate);
        List<CompareReport.TextChange> changes = new ArrayList<>();
        List<Match> matches = new ArrayList<>();
        int added = 0;
        int removed = 0;
        int baselineIndex = 0;
        int candidateIndex = 0;
        while (baselineIndex < baseline.size()
                || candidateIndex < candidate.size()) {
            if (baselineIndex < baseline.size()
                    && candidateIndex < candidate.size()
                    && baseline.get(baselineIndex).text().equals(
                        candidate.get(candidateIndex).text())) {
                matches.add(new Match(
                    baseline.get(baselineIndex),
                    candidate.get(candidateIndex)
                ));
                baselineIndex++;
                candidateIndex++;
            } else if (candidateIndex >= candidate.size()
                    || (baselineIndex < baseline.size()
                        && matrix[baselineIndex + 1][candidateIndex]
                            >= matrix[baselineIndex][candidateIndex + 1])) {
                removed++;
                changes.add(change(
                    "removed",
                    baselineIndex + 1,
                    null,
                    baseline.get(baselineIndex).text()
                ));
                baselineIndex++;
            } else {
                added++;
                changes.add(change(
                    "added",
                    null,
                    candidateIndex + 1,
                    candidate.get(candidateIndex).text()
                ));
                candidateIndex++;
            }
        }
        CompareReport.TextComparison text =
            new CompareReport.TextComparison(
                added > 0 || removed > 0,
                baseline.size(),
                candidate.size(),
                added,
                removed,
                List.copyOf(changes)
            );
        CompareReport.LayoutComparison layout =
            new CompareReport.LayoutComparison(
                geometryChanged(baselineSpace, candidateSpace)
                    || moved(matches) > 0,
                geometryChanged(baselineSpace, candidateSpace),
                moved(matches),
                geometry(baselineSpace),
                geometry(candidateSpace)
            );
        return new Result(text, layout);
    }

    private List<Line> lines(
            List<PdfPageContent.TextLine> source,
            VisualPageSpace space) {
        if (source.size() > properties.getMaxTextLinesPerPage()) {
            throw new OperationException(
                "COMPARE_TEXT_LINE_LIMIT_EXCEEDED",
                "A PDF page contains too many text lines to compare"
            );
        }
        if (space == null && !source.isEmpty()) {
            throw new IllegalStateException("Text requires page geometry");
        }
        List<Line> result = new ArrayList<>(source.size());
        for (PdfPageContent.TextLine line : source) {
            String text = line.text().replaceAll("\\s+", " ").strip();
            if (text.length() > properties.getMaxLineCharacters()) {
                throw new OperationException(
                    "COMPARE_TEXT_LINE_LIMIT_EXCEEDED",
                    "A PDF text line exceeds the comparison limit"
                );
            }
            if (!text.isEmpty()) {
                result.add(new Line(
                    text,
                    line.left() * space.userUnit(),
                    line.top() * space.userUnit(),
                    line.fontSize() * space.userUnit()
                ));
            }
        }
        return List.copyOf(result);
    }

    private void requireMatrix(int baseline, int candidate) {
        long cells;
        try {
            cells = Math.multiplyExact(
                (long) baseline + 1,
                (long) candidate + 1
            );
        } catch (ArithmeticException exception) {
            throw matrixLimit();
        }
        if (cells > properties.getMaxDiffMatrixCells()) {
            throw matrixLimit();
        }
    }

    private int[][] matrix(
            List<Line> baseline,
            List<Line> candidate) {
        int[][] matrix = new int[baseline.size() + 1][
            candidate.size() + 1
        ];
        for (int left = baseline.size() - 1; left >= 0; left--) {
            for (int right = candidate.size() - 1;
                    right >= 0;
                    right--) {
                matrix[left][right] = baseline.get(left).text().equals(
                    candidate.get(right).text())
                        ? 1 + matrix[left + 1][right + 1]
                        : Math.max(
                            matrix[left + 1][right],
                            matrix[left][right + 1]
                        );
            }
        }
        return matrix;
    }

    private CompareReport.TextChange change(
            String type,
            Integer baselineLine,
            Integer candidateLine,
            String text) {
        totalChanges++;
        if (totalChanges > properties.getMaxTextChanges()) {
            throw new OperationException(
                "COMPARE_TEXT_CHANGE_LIMIT_EXCEEDED",
                "The PDFs contain too many text changes to report safely"
            );
        }
        return new CompareReport.TextChange(
            type,
            baselineLine,
            candidateLine,
            text
        );
    }

    private int moved(List<Match> matches) {
        int moved = 0;
        for (Match match : matches) {
            if (difference(
                    match.baseline().left(),
                    match.candidate().left())
                    || difference(
                        match.baseline().top(),
                        match.candidate().top())
                    || difference(
                        match.baseline().fontSize(),
                        match.candidate().fontSize())) {
                moved++;
            }
        }
        return moved;
    }

    private boolean geometryChanged(
            VisualPageSpace baseline,
            VisualPageSpace candidate) {
        if (baseline == null || candidate == null) {
            return baseline != candidate;
        }
        return difference(
                baseline.width() * baseline.userUnit(),
                candidate.width() * candidate.userUnit())
            || difference(
                baseline.height() * baseline.userUnit(),
                candidate.height() * candidate.userUnit())
            || baseline.rotation() != candidate.rotation();
    }

    private CompareReport.PageGeometry geometry(
            VisualPageSpace space) {
        return space == null
            ? null
            : new CompareReport.PageGeometry(
                rounded(space.width() * space.userUnit()),
                rounded(space.height() * space.userUnit()),
                space.rotation()
            );
    }

    private boolean difference(double left, double right) {
        return Math.abs(left - right) > layoutTolerancePoints;
    }

    private double rounded(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    private OperationException matrixLimit() {
        return new OperationException(
            "COMPARE_TEXT_COMPLEXITY_LIMIT_EXCEEDED",
            "A PDF page is too complex for bounded text comparison"
        );
    }

    record Result(
        CompareReport.TextComparison text,
        CompareReport.LayoutComparison layout
    ) {
    }

    private record Line(
        String text,
        double left,
        double top,
        double fontSize
    ) {
    }

    private record Match(Line baseline, Line candidate) {
    }
}
