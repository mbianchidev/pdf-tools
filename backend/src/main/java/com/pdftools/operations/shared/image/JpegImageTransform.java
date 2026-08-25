package com.pdftools.operations.shared.image;

import org.apache.pdfbox.util.Matrix;

public final class JpegImageTransform {

    private JpegImageTransform() {
    }

    public static Matrix matrix(
            JpegInspector.JpegInfo info,
            float x,
            float y,
            float width,
            float height) {
        return switch (info.orientation()) {
            case 2 -> new Matrix(
                -width,
                0,
                0,
                height,
                x + width,
                y
            );
            case 3 -> new Matrix(
                -width,
                0,
                0,
                -height,
                x + width,
                y + height
            );
            case 4 -> new Matrix(
                width,
                0,
                0,
                -height,
                x,
                y + height
            );
            case 5 -> new Matrix(
                0,
                -height,
                -width,
                0,
                x + width,
                y + height
            );
            case 6 -> new Matrix(
                0,
                -height,
                width,
                0,
                x,
                y + height
            );
            case 7 -> new Matrix(
                0,
                height,
                width,
                0,
                x,
                y
            );
            case 8 -> new Matrix(
                0,
                height,
                -width,
                0,
                x + width,
                y
            );
            default -> new Matrix(width, 0, 0, height, x, y);
        };
    }
}
