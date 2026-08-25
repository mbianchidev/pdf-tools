package com.pdftools.operations.pdfword;

import com.pdftools.operations.OperationException;

final class PdfToWordImageBudget {

    private final PdfToWordProperties properties;
    private int images;
    private long pixels;
    private long bytes;

    PdfToWordImageBudget(PdfToWordProperties properties) {
        this.properties = properties;
    }

    void claimBytes(long imageBytes) {
        try {
            bytes = Math.addExact(bytes, imageBytes);
        } catch (ArithmeticException exception) {
            throw exceeded();
        }
        if (imageBytes < 1
                || imageBytes > properties.getMaxImageBytes()
                || bytes > properties.getMaxTotalImageBytes()) {
            throw exceeded();
        }
    }

    void claim(int width, int height) {
        long nextPixels;
        try {
            nextPixels = Math.multiplyExact((long) width, height);
            pixels = Math.addExact(pixels, nextPixels);
            images = Math.addExact(images, 1);
        } catch (ArithmeticException exception) {
            throw exceeded();
        }
        if (width < 1
                || height < 1
                || width > properties.getMaxImageDimension()
                || height > properties.getMaxImageDimension()
                || nextPixels > properties.getMaxPixelsPerImage()
                || pixels > properties.getMaxTotalImagePixels()
                || images > properties.getMaxImages()) {
            throw exceeded();
        }
    }

    private OperationException exceeded() {
        return new OperationException(
            "PDF_WORD_IMAGE_LIMIT_EXCEEDED",
            "The PDF exceeds the configured image limit"
        );
    }
}
