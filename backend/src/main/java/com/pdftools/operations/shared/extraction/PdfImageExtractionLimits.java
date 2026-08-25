package com.pdftools.operations.shared.extraction;

public interface PdfImageExtractionLimits {

    int getMaxImages();

    long getMaxPixelsPerImage();

    long getMaxTotalImagePixels();

    long getMaxImageBytes();

    long getMaxTotalImageBytes();

    int getMaxImageDimension();
}
