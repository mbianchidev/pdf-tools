package com.pdftools.operations.edit;

import com.pdftools.operations.OperationException;
import com.pdftools.operations.shared.coordinates.PdfRectangle;
import com.pdftools.operations.shared.coordinates.VisualPageSpace;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.color.PDColor;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceRGB;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationHighlight;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationText;
import org.apache.pdfbox.util.Matrix;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.awt.geom.AffineTransform;
import java.io.IOException;
import java.util.List;

@Component
public class EditRenderer {

    private static final float BEZIER = 0.5522848f;

    public void render(
            PDDocument document,
            PDPage page,
            List<EditPlanFactory.EditElement> elements,
            EditImageProvider imageProvider,
            Runnable cancellationCheck) {
        VisualPageSpace space = VisualPageSpace.from(page);
        boolean hasContent = elements.stream().anyMatch(
            element -> !(element
                instanceof EditPlanFactory.HighlightElement)
                && !(element instanceof EditPlanFactory.NoteElement)
        );
        try (PDPageContentStream content = hasContent
                ? content(document, page)
                : null) {
            for (EditPlanFactory.EditElement element : elements) {
                cancellationCheck.run();
                switch (element) {
                    case EditPlanFactory.TextElement text ->
                        drawText(content, space, text);
                    case EditPlanFactory.ImageElement image ->
                        drawImage(
                            document,
                            content,
                            space,
                            image,
                            imageProvider,
                            cancellationCheck
                        );
                    case EditPlanFactory.ShapeElement shape ->
                        drawShape(content, space, shape);
                    case EditPlanFactory.LineElement line ->
                        drawLine(content, space, line);
                    case EditPlanFactory.HighlightElement highlight ->
                        addHighlight(document, page, space, highlight);
                    case EditPlanFactory.NoteElement note ->
                        addNote(page, space, note);
                }
            }
        } catch (IOException | IllegalArgumentException exception) {
            throw renderFailure(exception);
        }
    }

    private void drawText(
            PDPageContentStream content,
            VisualPageSpace space,
            EditPlanFactory.TextElement element) throws IOException {
        PDType1Font font = new PDType1Font(element.font());
        float fontSize = element.fontSize() / space.userUnit();
        float textWidth = font.getStringWidth(element.text())
            / 1000f
            * fontSize;
        AffineTransform transform = space.centeredTransform(
            element.x(),
            element.y(),
            element.rotation()
        );
        transform.translate(
            -textWidth / 2,
            -fontSize * 0.35
        );
        prepare(
            content,
            space,
            element.opacity(),
            element.color(),
            null,
            1
        );
        content.beginText();
        content.setFont(font, fontSize);
        content.setTextMatrix(new Matrix(transform));
        content.showText(element.text());
        content.endText();
        content.restoreGraphicsState();
    }

    private void drawImage(
            PDDocument document,
            PDPageContentStream content,
            VisualPageSpace space,
            EditPlanFactory.ImageElement element,
            EditImageProvider imageProvider,
            Runnable cancellationCheck) throws IOException {
        EditImageProvider.ImageAsset asset = imageProvider.get(
            element.imageIndex(),
            document,
            cancellationCheck
        );
        float width = space.width() * element.width();
        float height = width
            * asset.displayHeight()
            / asset.displayWidth();
        if (height > space.height()) {
            float scale = space.height() / height;
            width *= scale;
            height *= scale;
        }
        prepare(
            content,
            space,
            element.opacity(),
            null,
            null,
            1
        );
        content.transform(new Matrix(space.centeredTransform(
            element.x(),
            element.y(),
            element.rotation()
        )));
        content.drawImage(
            asset.image(),
            asset.matrix(
                -width / 2,
                -height / 2,
                width,
                height
            )
        );
        content.restoreGraphicsState();
    }

    private void drawShape(
            PDPageContentStream content,
            VisualPageSpace space,
            EditPlanFactory.ShapeElement element) throws IOException {
        float x = space.visualX(element.x());
        float top = space.visualY(element.y());
        float width = space.width() * element.width();
        float height = space.height() * element.height();
        float y = top - height;
        prepare(
            content,
            space,
            element.opacity(),
            element.fillColor(),
            element.strokeColor(),
            element.strokeWidth() / space.userUnit()
        );
        if (element.type() == EditPlanFactory.ElementType.RECTANGLE) {
            content.addRect(x, y, width, height);
        } else {
            ellipse(content, x, y, width, height);
        }
        paint(content, element.fillColor(), element.strokeColor());
        content.restoreGraphicsState();
    }

    private void drawLine(
            PDPageContentStream content,
            VisualPageSpace space,
            EditPlanFactory.LineElement element) throws IOException {
        prepare(
            content,
            space,
            element.opacity(),
            null,
            element.color(),
            element.strokeWidth() / space.userUnit()
        );
        content.moveTo(
            space.visualX(element.x()),
            space.visualY(element.y())
        );
        content.lineTo(
            space.visualX(element.x2()),
            space.visualY(element.y2())
        );
        content.stroke();
        content.restoreGraphicsState();
    }

    private void addHighlight(
            PDDocument document,
            PDPage page,
            VisualPageSpace space,
            EditPlanFactory.HighlightElement element) {
        VisualPageSpace.Point upperLeft = space.toPdfPoint(
            element.x(),
            element.y()
        );
        VisualPageSpace.Point upperRight = space.toPdfPoint(
            element.x() + element.width(),
            element.y()
        );
        VisualPageSpace.Point lowerLeft = space.toPdfPoint(
            element.x(),
            element.y() + element.height()
        );
        VisualPageSpace.Point lowerRight = space.toPdfPoint(
            element.x() + element.width(),
            element.y() + element.height()
        );
        float minimumX = Math.min(
            Math.min(upperLeft.x(), upperRight.x()),
            Math.min(lowerLeft.x(), lowerRight.x())
        );
        float maximumX = Math.max(
            Math.max(upperLeft.x(), upperRight.x()),
            Math.max(lowerLeft.x(), lowerRight.x())
        );
        float minimumY = Math.min(
            Math.min(upperLeft.y(), upperRight.y()),
            Math.min(lowerLeft.y(), lowerRight.y())
        );
        float maximumY = Math.max(
            Math.max(upperLeft.y(), upperRight.y()),
            Math.max(lowerLeft.y(), lowerRight.y())
        );
        PDRectangle bounds = new PDRectangle(
            minimumX,
            minimumY,
            maximumX - minimumX,
            maximumY - minimumY
        );
        PDAnnotationHighlight annotation = new PDAnnotationHighlight();
        annotation.setRectangle(bounds);
        annotation.setPage(page);
        annotation.setColor(pdfColor(element.color()));
        annotation.setConstantOpacity(element.opacity());
        annotation.setQuadPoints(new float[]{
            upperLeft.x(), upperLeft.y(),
            upperRight.x(), upperRight.y(),
            lowerLeft.x(), lowerLeft.y(),
            lowerRight.x(), lowerRight.y()
        });
        annotation.constructAppearances(document);
        addAnnotation(page, annotation);
    }

    private void addNote(
            PDPage page,
            VisualPageSpace space,
            EditPlanFactory.NoteElement element) {
        VisualPageSpace.Point center = space.toPdfPoint(
            element.x(),
            element.y()
        );
        float iconWidth = 18 / space.userUnit();
        float iconHeight = 20 / space.userUnit();
        PDAnnotationText annotation = new PDAnnotationText();
        annotation.setRectangle(new PDRectangle(
            center.x() - iconWidth / 2,
            center.y() - iconHeight / 2,
            iconWidth,
            iconHeight
        ));
        annotation.setPage(page);
        annotation.setContents(element.contents());
        annotation.setTitlePopup(element.title());
        annotation.setColor(pdfColor(element.color()));
        annotation.setName(PDAnnotationText.NAME_NOTE);
        annotation.setOpen(false);
        addAnnotation(page, annotation);
    }

    private void addAnnotation(
            PDPage page,
            org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation
                annotation) {
        try {
            page.getAnnotations().add(annotation);
        } catch (IOException exception) {
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

    private void prepare(
            PDPageContentStream content,
            VisualPageSpace space,
            float opacity,
            EditPlanFactory.RgbColor fill,
            EditPlanFactory.RgbColor stroke,
            float strokeWidth) throws IOException {
        content.saveGraphicsState();
        PDExtendedGraphicsState state = new PDExtendedGraphicsState();
        state.setNonStrokingAlphaConstant(opacity);
        state.setStrokingAlphaConstant(opacity);
        content.setGraphicsStateParameters(state);
        content.transform(space.pageTransform());
        if (fill != null) {
            content.setNonStrokingColor(awtColor(fill));
        }
        if (stroke != null) {
            content.setStrokingColor(awtColor(stroke));
            content.setLineWidth(strokeWidth);
        }
    }

    private void paint(
            PDPageContentStream content,
            EditPlanFactory.RgbColor fill,
            EditPlanFactory.RgbColor stroke) throws IOException {
        if (fill != null && stroke != null) {
            content.fillAndStroke();
        } else if (fill != null) {
            content.fill();
        } else {
            content.stroke();
        }
    }

    private void ellipse(
            PDPageContentStream content,
            float x,
            float y,
            float width,
            float height) throws IOException {
        float ox = width / 2 * BEZIER;
        float oy = height / 2 * BEZIER;
        float centerX = x + width / 2;
        float centerY = y + height / 2;
        content.moveTo(centerX + width / 2, centerY);
        content.curveTo(
            centerX + width / 2,
            centerY + oy,
            centerX + ox,
            centerY + height / 2,
            centerX,
            centerY + height / 2
        );
        content.curveTo(
            centerX - ox,
            centerY + height / 2,
            centerX - width / 2,
            centerY + oy,
            centerX - width / 2,
            centerY
        );
        content.curveTo(
            centerX - width / 2,
            centerY - oy,
            centerX - ox,
            centerY - height / 2,
            centerX,
            centerY - height / 2
        );
        content.curveTo(
            centerX + ox,
            centerY - height / 2,
            centerX + width / 2,
            centerY - oy,
            centerX + width / 2,
            centerY
        );
        content.closePath();
    }

    private Color awtColor(EditPlanFactory.RgbColor color) {
        return new Color(color.red(), color.green(), color.blue());
    }

    private PDColor pdfColor(EditPlanFactory.RgbColor color) {
        return new PDColor(
            new float[]{
                color.red() / 255f,
                color.green() / 255f,
                color.blue() / 255f
            },
            PDDeviceRGB.INSTANCE
        );
    }

    private OperationException renderFailure(Exception exception) {
        return new OperationException(
            "EDIT_RENDER_FAILED",
            "An edit element could not be rendered",
            exception
        );
    }
}
