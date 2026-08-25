package com.pdftools.operations.shared.coordinates;

public record NormalizedRectangle(
    double x,
    double y,
    double width,
    double height
) {
    private static final double EPSILON = 0.000001;

    public NormalizedRectangle {
        if (!isFinite(x) || !isFinite(y) || !isFinite(width) || !isFinite(height)) {
            throw new IllegalArgumentException("Rectangle values must be finite");
        }
        if (x < 0 || y < 0 || width <= 0 || height <= 0
                || x + width > 1 + EPSILON || y + height > 1 + EPSILON) {
            throw new IllegalArgumentException(
                "Normalized rectangle must be positive and contained within the page"
            );
        }
    }

    private static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
