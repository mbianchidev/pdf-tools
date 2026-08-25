package com.pdftools.operations.pdfword;

import com.pdftools.operations.BoundedOutputStream;
import com.pdftools.operations.OperationException;
import com.pdftools.operations.OutputLimitExceededException;
import com.pdftools.operations.shared.coordinates.VisualPageSpace;
import org.apache.pdfbox.contentstream.PDFGraphicsStreamEngine;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.graphics.image.PDImage;

import javax.imageio.ImageIO;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

final class PdfToWordImageExtractor extends PDFGraphicsStreamEngine {

    private final PdfToWordProperties properties;
    private final PdfToWordImageBudget budget;
    private final VisualPageSpace pageSpace;
    private final List<PdfToWordPage.PageImage> images =
        new ArrayList<>();
    private Point2D currentPoint;

    PdfToWordImageExtractor(
            PDPage page,
            PdfToWordProperties properties,
            PdfToWordImageBudget budget) {
        super(page);
        this.properties = properties;
        this.budget = budget;
        this.pageSpace = VisualPageSpace.from(page);
    }

    List<PdfToWordPage.PageImage> extract() throws IOException {
        processPage(getPage());
        return List.copyOf(images);
    }

    @Override
    public void drawImage(PDImage image) throws IOException {
        budget.claim(image.getWidth(), image.getHeight());
        Bounds bounds = bounds();
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             BoundedOutputStream bounded = new BoundedOutputStream(
                 bytes,
                 properties.getMaxImageBytes(),
                 () -> {
                 }
             )) {
            if (!ImageIO.write(image.getImage(), "png", bounded)) {
                throw invalidImage(null);
            }
            byte[] png = bytes.toByteArray();
            budget.claimBytes(png.length);
            images.add(new PdfToWordPage.PageImage(
                png,
                bounds.left(),
                bounds.top(),
                bounds.width(),
                bounds.height()
            ));
        } catch (OutputLimitExceededException exception) {
            throw imageLimit();
        } catch (RuntimeException exception) {
            throw exception;
        } catch (IOException exception) {
            throw invalidImage(exception);
        }
    }

    private Bounds bounds() {
        var matrix = getGraphicsState().getCurrentTransformationMatrix();
        Point2D[] points = {
            matrix.transformPoint(0, 0),
            matrix.transformPoint(1, 0),
            matrix.transformPoint(0, 1),
            matrix.transformPoint(1, 1)
        };
        try {
            AffineTransform inverse = pageSpace.pageTransform()
                .createAffineTransform()
                .createInverse();
            double minX = Double.POSITIVE_INFINITY;
            double maxX = Double.NEGATIVE_INFINITY;
            double minY = Double.POSITIVE_INFINITY;
            double maxY = Double.NEGATIVE_INFINITY;
            for (Point2D point : points) {
                Point2D local = inverse.transform(point, null);
                minX = Math.min(minX, local.getX());
                maxX = Math.max(maxX, local.getX());
                minY = Math.min(minY, local.getY());
                maxY = Math.max(maxY, local.getY());
            }
            return new Bounds(
                (float) minX,
                pageSpace.height() - (float) maxY,
                Math.max((float) (maxX - minX), 1f),
                Math.max((float) (maxY - minY), 1f)
            );
        } catch (NoninvertibleTransformException exception) {
            throw new OperationException(
                "INVALID_PDF_IMAGE_TRANSFORM",
                "The PDF contains an invalid image transform",
                exception
            );
        }
    }

    private OperationException invalidImage(Throwable cause) {
        return new OperationException(
            "INVALID_PDF_IMAGE",
            "An embedded PDF image could not be decoded",
            cause
        );
    }

    private OperationException imageLimit() {
        return new OperationException(
            "PDF_WORD_IMAGE_LIMIT_EXCEEDED",
            "The PDF exceeds the configured image limit"
        );
    }

    @Override
    public void appendRectangle(
            Point2D p0,
            Point2D p1,
            Point2D p2,
            Point2D p3) {
    }

    @Override
    public void clip(int windingRule) {
    }

    @Override
    public void moveTo(float x, float y) {
        currentPoint = new Point2D.Float(x, y);
    }

    @Override
    public void lineTo(float x, float y) {
        currentPoint = new Point2D.Float(x, y);
    }

    @Override
    public void curveTo(
            float x1,
            float y1,
            float x2,
            float y2,
            float x3,
            float y3) {
        currentPoint = new Point2D.Float(x3, y3);
    }

    @Override
    public Point2D getCurrentPoint() {
        return currentPoint;
    }

    @Override
    public void closePath() {
    }

    @Override
    public void endPath() {
    }

    @Override
    public void strokePath() {
    }

    @Override
    public void fillPath(int windingRule) {
    }

    @Override
    public void fillAndStrokePath(int windingRule) {
    }

    @Override
    public void shadingFill(COSName shadingName) {
    }

    private record Bounds(
        float left,
        float top,
        float width,
        float height
    ) {
    }
}
