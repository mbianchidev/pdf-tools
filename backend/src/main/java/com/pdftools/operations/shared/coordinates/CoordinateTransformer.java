package com.pdftools.operations.shared.coordinates;

import org.springframework.stereotype.Component;

@Component
public class CoordinateTransformer {

    public PdfRectangle toPdfRectangle(
            NormalizedRectangle rectangle,
            PageGeometry page) {
        double visualWidth = page.rotation() % 180 == 0 ? page.width() : page.height();
        double visualHeight = page.rotation() % 180 == 0 ? page.height() : page.width();

        double x = rectangle.x() * visualWidth;
        double y = rectangle.y() * visualHeight;
        double width = rectangle.width() * visualWidth;
        double height = rectangle.height() * visualHeight;

        return switch (page.rotation()) {
            case 0 -> new PdfRectangle(
                page.lowerLeftX() + x,
                page.lowerLeftY() + page.height() - y - height,
                width,
                height
            );
            case 90 -> new PdfRectangle(
                page.lowerLeftX() + y,
                page.lowerLeftY() + x,
                height,
                width
            );
            case 180 -> new PdfRectangle(
                page.lowerLeftX() + page.width() - x - width,
                page.lowerLeftY() + y,
                width,
                height
            );
            case 270 -> new PdfRectangle(
                page.lowerLeftX() + page.width() - y - height,
                page.lowerLeftY() + page.height() - x - width,
                height,
                width
            );
            default -> throw new IllegalStateException("Unsupported page rotation");
        };
    }
}
