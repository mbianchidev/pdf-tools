package com.pdftools.operations.shared.extraction;

import com.pdftools.operations.OperationException;

public final class PdfImageExtractionBudget {

    private final PdfImageExtractionLimits properties;
    private final String codePrefix;
    private final String documentLabel;
    private int images;
    private long pixels;
    private long bytes;

    public PdfImageExtractionBudget(
            PdfImageExtractionLimits properties,
            String codePrefix,
            String documentLabel) {
        this.properties = properties;
        this.codePrefix = codePrefix;
        this.documentLabel = documentLabel;
    }

    public void claimBytes(long imageBytes) {
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

    public void claim(int width, int height) {
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

    public OperationException exceeded() {
        return new OperationException(
            codePrefix + "_IMAGE_LIMIT_EXCEEDED",
            "The PDF exceeds the configured " + documentLabel
                + " image limit"
        );
    }
}
