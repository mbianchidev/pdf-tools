package com.pdftools.operations.shared.extraction;

import java.util.ArrayList;
import java.util.List;

public final class AlignedTableDetector {

    private final int maxColumns;

    public AlignedTableDetector(int maxColumns) {
        if (maxColumns < 2) {
            throw new IllegalArgumentException(
                "maxColumns must be at least two"
            );
        }
        this.maxColumns = maxColumns;
    }

    public TableCandidate tableAt(
            List<PdfPageContent.TextLine> lines,
            int start) {
        List<Cell> first = cells(lines.get(start));
        if (first.size() < 2 || first.size() > maxColumns) {
            return null;
        }
        List<List<Cell>> rows = new ArrayList<>();
        rows.add(first);
        for (int index = start + 1; index < lines.size(); index++) {
            List<Cell> candidate = cells(lines.get(index));
            if (!aligned(first, candidate)) {
                break;
            }
            rows.add(candidate);
        }
        return rows.size() >= 2
            ? new TableCandidate(List.copyOf(rows))
            : null;
    }

    private List<Cell> cells(PdfPageContent.TextLine line) {
        List<Cell> cells = new ArrayList<>();
        List<PdfPageContent.TextWord> current = new ArrayList<>();
        PdfPageContent.TextWord previous = null;
        for (PdfPageContent.TextWord word : line.words()) {
            float gap = previous == null ? 0 : word.left() - previous.right();
            float threshold = Math.max(10, line.fontSize() * 0.65f);
            if (previous != null && gap > threshold) {
                cells.add(cell(current));
                current.clear();
            }
            current.add(word);
            previous = word;
        }
        if (!current.isEmpty()) {
            cells.add(cell(current));
        }
        return List.copyOf(cells);
    }

    private Cell cell(List<PdfPageContent.TextWord> words) {
        return new Cell(
            words.getFirst().left(),
            words.stream().map(PdfPageContent.TextWord::right)
                .max(Float::compare)
                .orElse(words.getFirst().right()),
            words.stream().map(PdfPageContent.TextWord::text)
                .reduce((left, right) -> left + " " + right)
                .orElse("")
        );
    }

    private boolean aligned(List<Cell> expected, List<Cell> actual) {
        if (expected.size() != actual.size()) {
            return false;
        }
        for (int index = 0; index < expected.size(); index++) {
            if (Math.abs(
                    expected.get(index).left()
                        - actual.get(index).left()) > 18) {
                return false;
            }
        }
        return true;
    }

    public record Cell(float left, float right, String text) {
    }

    public record TableCandidate(List<List<Cell>> rows) {
    }
}
