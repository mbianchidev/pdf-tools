package com.pdftools.operations.shared.coordinates;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CoordinateTransformerTest {

    private static final double DELTA = 0.000001;
    private final CoordinateTransformer transformer = new CoordinateTransformer();
    private final NormalizedRectangle selection = new NormalizedRectangle(0.1, 0.2, 0.3, 0.4);

    @Test
    void convertsUnrotatedTopLeftCoordinatesToPdfSpace() {
        assertRectangle(
            new PdfRectangle(30, 180, 60, 160),
            transformer.toPdfRectangle(selection, new PageGeometry(10, 20, 200, 400, 0))
        );
    }

    @Test
    void convertsNinetyDegreeCoordinatesToPdfSpace() {
        assertRectangle(
            new PdfRectangle(50, 60, 80, 120),
            transformer.toPdfRectangle(selection, new PageGeometry(10, 20, 200, 400, 90))
        );
    }

    @Test
    void convertsOneHundredEightyDegreeCoordinatesToPdfSpace() {
        assertRectangle(
            new PdfRectangle(130, 100, 60, 160),
            transformer.toPdfRectangle(selection, new PageGeometry(10, 20, 200, 400, 180))
        );
    }

    @Test
    void convertsTwoHundredSeventyDegreeCoordinatesToPdfSpace() {
        assertRectangle(
            new PdfRectangle(90, 260, 80, 120),
            transformer.toPdfRectangle(selection, new PageGeometry(10, 20, 200, 400, 270))
        );
    }

    private void assertRectangle(PdfRectangle expected, PdfRectangle actual) {
        assertEquals(expected.x(), actual.x(), DELTA);
        assertEquals(expected.y(), actual.y(), DELTA);
        assertEquals(expected.width(), actual.width(), DELTA);
        assertEquals(expected.height(), actual.height(), DELTA);
    }
}
