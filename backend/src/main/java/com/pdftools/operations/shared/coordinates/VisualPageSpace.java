package com.pdftools.operations.shared.coordinates;

import com.pdftools.operations.OperationException;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.util.Matrix;

import java.awt.geom.AffineTransform;

public record VisualPageSpace(
    PDRectangle cropBox,
    int rotation,
    float userUnit,
    float width,
    float height,
    Matrix pageTransform
) {

    public static VisualPageSpace from(PDPage page) {
        float userUnit = page.getUserUnit();
        if (!Float.isFinite(userUnit) || userUnit <= 0) {
            throw new OperationException(
                "INVALID_PAGE_USER_UNIT",
                "Page UserUnit must be a positive finite number"
            );
        }
        PDRectangle box = page.getCropBox();
        int rotation = Math.floorMod(page.getRotation(), 360);
        if (rotation % 90 != 0) {
            throw new OperationException(
                "UNSUPPORTED_PAGE_ROTATION",
                "Page rotation must be a multiple of 90 degrees"
            );
        }
        float visualWidth = rotation % 180 == 0
            ? box.getWidth()
            : box.getHeight();
        float visualHeight = rotation % 180 == 0
            ? box.getHeight()
            : box.getWidth();
        return new VisualPageSpace(
            box,
            rotation,
            userUnit,
            visualWidth,
            visualHeight,
            pageTransform(box, rotation)
        );
    }

    public float visualX(float normalizedX) {
        return normalizedX * width;
    }

    public float visualY(float normalizedTopY) {
        return (1 - normalizedTopY) * height;
    }

    public PdfRectangle toPdfRectangle(
            float x,
            float y,
            float width,
            float height) {
        return new CoordinateTransformer().toPdfRectangle(
            new NormalizedRectangle(x, y, width, height),
            new PageGeometry(
                cropBox.getLowerLeftX(),
                cropBox.getLowerLeftY(),
                cropBox.getWidth(),
                cropBox.getHeight(),
                rotation
            )
        );
    }

    public Point toPdfPoint(float normalizedX, float normalizedTopY) {
        float visualX = visualX(normalizedX);
        float visualYDown = normalizedTopY * height;
        return switch (rotation) {
            case 0 -> new Point(
                cropBox.getLowerLeftX() + visualX,
                cropBox.getLowerLeftY()
                    + cropBox.getHeight()
                    - visualYDown
            );
            case 90 -> new Point(
                cropBox.getLowerLeftX() + visualYDown,
                cropBox.getLowerLeftY() + visualX
            );
            case 180 -> new Point(
                cropBox.getLowerLeftX()
                    + cropBox.getWidth()
                    - visualX,
                cropBox.getLowerLeftY() + visualYDown
            );
            case 270 -> new Point(
                cropBox.getLowerLeftX()
                    + cropBox.getWidth()
                    - visualYDown,
                cropBox.getLowerLeftY()
                    + cropBox.getHeight()
                    - visualX
            );
            default -> throw new IllegalStateException(
                "Unsupported page rotation"
            );
        };
    }

    public AffineTransform centeredTransform(
            float normalizedX,
            float normalizedTopY,
            float clockwiseRotation) {
        AffineTransform transform = new AffineTransform();
        transform.translate(
            visualX(normalizedX),
            visualY(normalizedTopY)
        );
        transform.rotate(Math.toRadians(-clockwiseRotation));
        return transform;
    }

    private static Matrix pageTransform(
            PDRectangle box,
            int rotation) {
        float x = box.getLowerLeftX();
        float y = box.getLowerLeftY();
        float width = box.getWidth();
        float height = box.getHeight();
        return switch (rotation) {
            case 0 -> new Matrix(1, 0, 0, 1, x, y);
            case 90 -> new Matrix(0, 1, -1, 0, x + width, y);
            case 180 -> new Matrix(
                -1,
                0,
                0,
                -1,
                x + width,
                y + height
            );
            case 270 -> new Matrix(0, -1, 1, 0, x, y + height);
            default -> throw new IllegalStateException(
                "Unsupported page rotation"
            );
        };
    }

    public record Point(float x, float y) {
    }
}
