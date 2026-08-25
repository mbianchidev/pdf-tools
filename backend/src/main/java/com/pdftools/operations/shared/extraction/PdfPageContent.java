package com.pdftools.operations.shared.extraction;

import java.util.List;

public record PdfPageContent(
    float width,
    float height,
    float userUnit,
    List<TextLine> lines,
    List<PageImage> images
) {
    public record TextLine(
        float left,
        float top,
        float fontSize,
        List<TextWord> words
    ) {
        public String text() {
            return words.stream()
                .map(TextWord::text)
                .reduce((left, right) -> left + " " + right)
                .orElse("");
        }
    }

    public record TextWord(
        String text,
        float left,
        float top,
        float width,
        float height,
        float fontSize,
        boolean bold
    ) {
        public float right() {
            return left + width;
        }
    }

    public record PageImage(
        byte[] png,
        float left,
        float top,
        float width,
        float height
    ) {
    }
}
