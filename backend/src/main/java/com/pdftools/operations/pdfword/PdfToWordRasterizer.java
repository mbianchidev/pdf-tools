package com.pdftools.operations.pdfword;

import com.pdftools.operations.BoundedOutputStream;
import com.pdftools.operations.OperationException;
import com.pdftools.operations.OutputLimitExceededException;
import com.pdftools.operations.shared.coordinates.VisualPageSpace;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

final class PdfToWordRasterizer {

    private final PdfToWordProperties properties;
    private final PdfToWordImageBudget budget;
    private final PDFRenderer renderer;

    PdfToWordRasterizer(
            PDDocument document,
            PdfToWordProperties properties,
            PdfToWordImageBudget budget) {
        this.properties = properties;
        this.budget = budget;
        this.renderer = new PDFRenderer(document);
        this.renderer.setSubsamplingAllowed(true);
    }

    PdfToWordPage.PageImage render(
            int pageIndex,
            VisualPageSpace pageSpace) {
        int width = dimension(pageSpace.width(), pageSpace.userUnit());
        int height = dimension(pageSpace.height(), pageSpace.userUnit());
        long pixels;
        try {
            pixels = Math.multiplyExact((long) width, height);
        } catch (ArithmeticException exception) {
            throw renderLimit();
        }
        if (pixels > properties.getMaxRenderPixelsPerPage()) {
            throw renderLimit();
        }
        budget.claim(width, height);
        try {
            var image = renderer.renderImageWithDPI(
                pageIndex,
                properties.getRenderDpi() * pageSpace.userUnit(),
                ImageType.RGB
            );
            if (image.getWidth() != width || image.getHeight() != height) {
                throw renderFailure(null);
            }
            try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                 BoundedOutputStream bounded = new BoundedOutputStream(
                     bytes,
                     properties.getMaxImageBytes(),
                     () -> {
                     }
                 )) {
                if (!ImageIO.write(image, "png", bounded)) {
                    throw renderFailure(null);
                }
                byte[] png = bytes.toByteArray();
                budget.claimBytes(png.length);
                return new PdfToWordPage.PageImage(
                    png,
                    0,
                    0,
                    pageSpace.width(),
                    pageSpace.height()
                );
            }
        } catch (OutputLimitExceededException exception) {
            throw renderLimit();
        } catch (OperationException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw renderFailure(exception);
        }
    }

    private int dimension(float points, float userUnit) {
        double pixels = Math.floor(
            points * userUnit * properties.getRenderDpi() / 72.0
        );
        if (!Double.isFinite(pixels)
                || pixels < 1
                || pixels > properties.getMaxImageDimension()) {
            throw renderLimit();
        }
        return (int) pixels;
    }

    private OperationException renderLimit() {
        return new OperationException(
            "PDF_WORD_RENDER_LIMIT_EXCEEDED",
            "A PDF page exceeds the configured render limit"
        );
    }

    private OperationException renderFailure(Throwable cause) {
        return new OperationException(
            "PDF_WORD_RENDER_FAILED",
            "A PDF page could not be rendered for Word",
            cause
        );
    }
}
