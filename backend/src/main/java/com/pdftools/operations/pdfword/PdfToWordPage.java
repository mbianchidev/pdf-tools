package com.pdftools.operations.pdfword;

import java.util.List;

record PdfToWordPage(
    float width,
    float height,
    float userUnit,
    List<TextLine> lines,
    List<PageImage> images
) {
    record TextLine(
        float left,
        float top,
        float fontSize,
        List<TextWord> words
    ) {
        String text() {
            return words.stream()
                .map(TextWord::text)
                .reduce((left, right) -> left + " " + right)
                .orElse("");
        }
    }

    record TextWord(
        String text,
        float left,
        float top,
        float width,
        float height,
        float fontSize,
        boolean bold
    ) {
        float right() {
            return left + width;
        }
    }

    record PageImage(
        byte[] png,
        float left,
        float top,
        float width,
        float height
    ) {
    }
}
