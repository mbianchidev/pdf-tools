package com.pdftools.operations.shared.coordinates;

public record PageGeometry(
    double lowerLeftX,
    double lowerLeftY,
    double width,
    double height,
    int rotation
) {
    public PageGeometry {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Page dimensions must be positive");
        }
        rotation = Math.floorMod(rotation, 360);
        if (rotation % 90 != 0) {
            throw new IllegalArgumentException("Page rotation must be a multiple of 90 degrees");
        }
    }
}
