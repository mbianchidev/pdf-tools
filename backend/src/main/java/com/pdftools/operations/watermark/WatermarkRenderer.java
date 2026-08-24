package com.pdftools.operations.watermark;

import com.pdftools.operations.OperationException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.apache.pdfbox.util.Matrix;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.awt.geom.AffineTransform;
import java.io.IOException;

@Component
public class WatermarkRenderer {

    public void drawText(
            PDDocument document,
            PDPage page,
            WatermarkPlanFactory.WatermarkPlan plan,
            PDType1Font font) {
        try {
            Geometry geometry = geometry(page, plan);
            float fontSize = plan.fontSize() / geometry.userUnit();
            float textWidth = font.getStringWidth(plan.text())
                / 1000f
                * fontSize;
            AffineTransform textTransform = elementTransform(
                geometry,
                plan.rotation()
            );
            textTransform.translate(
                -textWidth / 2,
                -fontSize * 0.35
            );
            try (PDPageContentStream content = content(document, page)) {
                prepareGraphics(content, geometry, plan.opacity());
                WatermarkPlanFactory.RgbColor color = plan.color();
                content.setNonStrokingColor(new Color(
                    color.red(),
                    color.green(),
                    color.blue()
                ));
                content.beginText();
                content.setFont(font, fontSize);
                content.setTextMatrix(new Matrix(textTransform));
                content.showText(plan.text());
                content.endText();
                content.restoreGraphicsState();
            }
        } catch (IOException | IllegalArgumentException exception) {
            throw renderFailure(exception);
        }
    }

    public void drawImage(
            PDDocument document,
            PDPage page,
            WatermarkPlanFactory.WatermarkPlan plan,
            WatermarkImagePreparer.PreparedImage prepared,
            PDImageXObject image) {
        try {
            Geometry geometry = geometry(page, plan);
            float maxWidth = geometry.visualWidth()
                * plan.imageWidthPercent()
                / 100f;
            float maxHeight = geometry.visualHeight() * 0.9f;
            float scale = Math.min(
                maxWidth / prepared.displayWidth(),
                maxHeight / prepared.displayHeight()
            );
            float width = prepared.displayWidth() * scale;
            float height = prepared.displayHeight() * scale;
            try (PDPageContentStream content = content(document, page)) {
                prepareGraphics(content, geometry, plan.opacity());
                content.transform(new Matrix(elementTransform(
                    geometry,
                    plan.rotation()
                )));
                content.drawImage(
                    image,
                    prepared.matrix(
                        -width / 2,
                        -height / 2,
                        width,
                        height
                    )
                );
                content.restoreGraphicsState();
            }
        } catch (IOException | IllegalArgumentException exception) {
            throw renderFailure(exception);
        }
    }

    private PDPageContentStream content(
            PDDocument document,
            PDPage page) throws IOException {
        return new PDPageContentStream(
            document,
            page,
            PDPageContentStream.AppendMode.APPEND,
            true,
            true
        );
    }

    private void prepareGraphics(
            PDPageContentStream content,
            Geometry geometry,
            float opacity) throws IOException {
        content.saveGraphicsState();
        PDExtendedGraphicsState state = new PDExtendedGraphicsState();
        state.setNonStrokingAlphaConstant(opacity);
        state.setStrokingAlphaConstant(opacity);
        content.setGraphicsStateParameters(state);
        content.transform(geometry.pageTransform());
    }

    private Geometry geometry(
            PDPage page,
            WatermarkPlanFactory.WatermarkPlan plan) {
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
        return new Geometry(
            visualWidth,
            visualHeight,
            plan.x() * visualWidth,
            (1 - plan.y()) * visualHeight,
            userUnit,
            pageTransform(box, rotation)
        );
    }

    private Matrix pageTransform(PDRectangle box, int rotation) {
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

    private AffineTransform elementTransform(
            Geometry geometry,
            float clockwiseRotation) {
        AffineTransform transform = new AffineTransform();
        transform.translate(geometry.centerX(), geometry.centerY());
        transform.rotate(Math.toRadians(-clockwiseRotation));
        return transform;
    }

    private OperationException renderFailure(Exception exception) {
        return new OperationException(
            "WATERMARK_RENDER_FAILED",
            "The watermark could not be rendered",
            exception
        );
    }

    private record Geometry(
        float visualWidth,
        float visualHeight,
        float centerX,
        float centerY,
        float userUnit,
        Matrix pageTransform
    ) {
    }
}
