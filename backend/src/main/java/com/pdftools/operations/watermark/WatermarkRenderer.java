package com.pdftools.operations.watermark;

import com.pdftools.operations.OperationException;
import com.pdftools.operations.shared.coordinates.VisualPageSpace;
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
            VisualPageSpace space = VisualPageSpace.from(page);
            float fontSize = plan.fontSize() / space.userUnit();
            float textWidth = font.getStringWidth(plan.text())
                / 1000f
                * fontSize;
            AffineTransform textTransform = space.centeredTransform(
                plan.x(),
                plan.y(),
                plan.rotation()
            );
            textTransform.translate(
                -textWidth / 2,
                -fontSize * 0.35
            );
            try (PDPageContentStream content = content(document, page)) {
                prepareGraphics(content, space, plan.opacity());
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
            VisualPageSpace space = VisualPageSpace.from(page);
            float maxWidth = space.width()
                * plan.imageWidthPercent()
                / 100f;
            float maxHeight = space.height() * 0.9f;
            float scale = Math.min(
                maxWidth / prepared.displayWidth(),
                maxHeight / prepared.displayHeight()
            );
            float width = prepared.displayWidth() * scale;
            float height = prepared.displayHeight() * scale;
            try (PDPageContentStream content = content(document, page)) {
                prepareGraphics(content, space, plan.opacity());
                content.transform(new Matrix(space.centeredTransform(
                    plan.x(),
                    plan.y(),
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
            VisualPageSpace space,
            float opacity) throws IOException {
        content.saveGraphicsState();
        PDExtendedGraphicsState state = new PDExtendedGraphicsState();
        state.setNonStrokingAlphaConstant(opacity);
        state.setStrokingAlphaConstant(opacity);
        content.setGraphicsStateParameters(state);
        content.transform(space.pageTransform());
    }

    private OperationException renderFailure(Exception exception) {
        return new OperationException(
            "WATERMARK_RENDER_FAILED",
            "The watermark could not be rendered",
            exception
        );
    }

}
