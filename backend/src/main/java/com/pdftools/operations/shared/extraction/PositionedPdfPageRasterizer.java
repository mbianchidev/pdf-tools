package com.pdftools.operations.shared.extraction;

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

public final class PositionedPdfPageRasterizer {

    private final PdfPageRasterizationLimits properties;
    private final PdfImageExtractionBudget budget;
    private final PDFRenderer renderer;
    private final String codePrefix;
    private final String documentLabel;

    public PositionedPdfPageRasterizer(
            PDDocument document,
            PdfPageRasterizationLimits properties,
            PdfImageExtractionBudget budget,
            String codePrefix,
            String documentLabel) {
        this.properties = properties;
        this.budget = budget;
        this.renderer = new PDFRenderer(document);
        this.renderer.setSubsamplingAllowed(true);
        this.codePrefix = codePrefix;
        this.documentLabel = documentLabel;
    }

    public PdfPageContent.PageImage render(
            int pageIndex,
            VisualPageSpace pageSpace) {
        RenderSize renderSize = renderSize(pageSpace);
        budget.claim(renderSize.width(), renderSize.height());
        try {
            var image = renderer.renderImage(
                pageIndex,
                renderSize.scale(),
                ImageType.RGB
            );
            if (image.getWidth() != renderSize.width()
                    || image.getHeight() != renderSize.height()) {
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
                return new PdfPageContent.PageImage(
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

    private RenderSize renderSize(VisualPageSpace pageSpace) {
        double requestedScale = pageSpace.userUnit()
            * properties.getRenderDpi() / 72.0;
        double requestedWidth = pageSpace.width() * requestedScale;
        double requestedHeight = pageSpace.height() * requestedScale;
        double requestedPixels = requestedWidth * requestedHeight;
        if (!Double.isFinite(requestedWidth)
                || !Double.isFinite(requestedHeight)
                || !Double.isFinite(requestedPixels)
                || requestedWidth < 1
                || requestedHeight < 1) {
            throw renderLimit();
        }
        double dimensionScale = Math.min(
            1,
            Math.min(
                properties.getMaxImageDimension() / requestedWidth,
                properties.getMaxImageDimension() / requestedHeight
            )
        );
        double maxPixels = Math.min(
            properties.getMaxRenderPixelsPerPage(),
            properties.getMaxPixelsPerImage()
        );
        double pixelScale = Math.min(
            1,
            Math.sqrt(maxPixels / requestedPixels)
        );
        double scale = requestedScale * Math.min(
            dimensionScale,
            pixelScale
        );
        if (!Double.isFinite(scale) || scale <= 0) {
            throw renderLimit();
        }
        int width = Math.max(
            (int) Math.floor(pageSpace.width() * scale),
            1
        );
        int height = Math.max(
            (int) Math.floor(pageSpace.height() * scale),
            1
        );
        return new RenderSize(width, height, (float) scale);
    }

    private OperationException renderLimit() {
        return new OperationException(
            codePrefix + "_RENDER_LIMIT_EXCEEDED",
            "A PDF page exceeds the configured " + documentLabel
                + " render limit"
        );
    }

    private OperationException renderFailure(Throwable cause) {
        return new OperationException(
            codePrefix + "_RENDER_FAILED",
            "A PDF page could not be rendered for " + documentLabel,
            cause
        );
    }

    private record RenderSize(int width, int height, float scale) {
    }
}
