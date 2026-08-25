package com.pdftools.operations.shared.extraction;

import com.pdftools.operations.OperationException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class PositionedPdfTextExtractor extends PDFTextStripper {

    private final int maxCharacters;
    private final String codePrefix;
    private final String documentLabel;
    private final List<Glyph> glyphs = new ArrayList<>();
    private int characters;

    public PositionedPdfTextExtractor(
            int maxCharacters,
            String codePrefix,
            String documentLabel)
            throws IOException {
        this.maxCharacters = maxCharacters;
        this.codePrefix = codePrefix;
        this.documentLabel = documentLabel;
        setSortByPosition(true);
        setSuppressDuplicateOverlappingText(true);
    }

    public List<PdfPageContent.TextLine> extract(
            PDDocument document,
            int pageIndex) throws IOException {
        glyphs.clear();
        setStartPage(pageIndex + 1);
        setEndPage(pageIndex + 1);
        getText(document);
        return lines();
    }

    @Override
    protected void writeString(
            String text,
            List<TextPosition> positions) {
        for (TextPosition position : positions) {
            String unicode = position.getUnicode();
            if (unicode == null || unicode.isEmpty()) {
                continue;
            }
            try {
                characters = Math.addExact(
                    characters,
                    unicode.length()
                );
            } catch (ArithmeticException exception) {
                throw textLimit();
            }
            if (characters > maxCharacters) {
                throw textLimit();
            }
            String fontName = position.getFont() == null
                ? ""
                : position.getFont().getName().toUpperCase(Locale.ROOT);
            glyphs.add(new Glyph(
                unicode,
                position.getXDirAdj(),
                position.getYDirAdj(),
                position.getWidthDirAdj(),
                position.getX(),
                position.getY(),
                position.getWidth(),
                position.getHeight(),
                position.getFontSizeInPt(),
                fontName.contains("BOLD")
                    || fontName.contains("BLACK")
                    || fontName.contains("SEMIBOLD")
            ));
        }
    }

    private List<PdfPageContent.TextLine> lines() {
        List<Glyph> ordered = glyphs.stream()
            .sorted(Comparator.comparing(Glyph::logicalTop)
                .thenComparing(Glyph::logicalLeft))
            .toList();
        List<List<Glyph>> grouped = new ArrayList<>();
        for (Glyph glyph : ordered) {
            List<Glyph> line = grouped.isEmpty()
                ? null
                : grouped.getLast();
            if (line == null || !sameLine(line, glyph)) {
                line = new ArrayList<>();
                grouped.add(line);
            }
            line.add(glyph);
        }
        return grouped.stream()
            .map(this::line)
            .filter(line -> !line.words().isEmpty())
            .toList();
    }

    private boolean sameLine(List<Glyph> line, Glyph candidate) {
        Glyph anchor = line.getFirst();
        float tolerance = Math.max(
            2f,
            Math.max(anchor.fontSize(), candidate.fontSize()) * 0.35f
        );
        return Math.abs(
            anchor.logicalTop() - candidate.logicalTop()
        ) <= tolerance;
    }

    private PdfPageContent.TextLine line(List<Glyph> source) {
        List<Glyph> ordered = source.stream()
            .sorted(Comparator.comparing(Glyph::logicalLeft))
            .toList();
        List<PdfPageContent.TextWord> words = new ArrayList<>();
        List<Glyph> current = new ArrayList<>();
        Glyph previous = null;
        for (Glyph glyph : ordered) {
            boolean whitespace = glyph.text().isBlank();
            float gap = previous == null
                ? 0
                : glyph.logicalLeft() - previous.logicalRight();
            float threshold = previous == null
                ? Float.MAX_VALUE
                : Math.max(1.5f, previous.fontSize() * 0.28f);
            if (whitespace || gap > threshold) {
                addWord(words, current);
                current.clear();
            }
            if (!whitespace) {
                current.add(glyph);
                previous = glyph;
            }
        }
        addWord(words, current);
        return new PdfPageContent.TextLine(
            words.isEmpty() ? 0 : words.getFirst().left(),
            ordered.stream().map(Glyph::visualTop)
                .min(Float::compare)
                .orElse(0f),
            ordered.stream().map(Glyph::fontSize)
                .max(Float::compare)
                .orElse(11f),
            List.copyOf(words)
        );
    }

    private void addWord(
            List<PdfPageContent.TextWord> words,
            List<Glyph> glyphs) {
        if (glyphs.isEmpty()) {
            return;
        }
        String text = glyphs.stream()
            .map(Glyph::text)
            .reduce("", String::concat);
        float left = glyphs.stream().map(Glyph::visualLeft)
            .min(Float::compare)
            .orElse(0f);
        float right = glyphs.stream().map(Glyph::visualRight)
            .max(Float::compare)
            .orElse(left);
        words.add(new PdfPageContent.TextWord(
            text,
            left,
            glyphs.stream().map(Glyph::visualTop)
                .min(Float::compare)
                .orElse(0f),
            Math.max(right - left, 1f),
            glyphs.stream().map(Glyph::height)
                .max(Float::compare)
                .orElse(1f),
            glyphs.stream().map(Glyph::fontSize)
                .max(Float::compare)
                .orElse(11f),
            glyphs.stream().anyMatch(Glyph::bold)
        ));
    }

    private OperationException textLimit() {
        return new OperationException(
            codePrefix + "_TEXT_LIMIT_EXCEEDED",
            "The PDF contains too much extractable text for "
                + documentLabel
        );
    }

    private record Glyph(
        String text,
        float logicalLeft,
        float logicalTop,
        float logicalWidth,
        float visualLeft,
        float visualTop,
        float visualWidth,
        float height,
        float fontSize,
        boolean bold
    ) {
        private float logicalRight() {
            return logicalLeft + logicalWidth;
        }

        private float visualRight() {
            return visualLeft + visualWidth;
        }
    }
}
