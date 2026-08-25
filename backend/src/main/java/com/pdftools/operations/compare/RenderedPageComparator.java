package com.pdftools.operations.compare;

import com.pdftools.operations.BoundedOutputStream;
import com.pdftools.operations.OperationException;
import com.pdftools.operations.OutputLimitExceededException;
import com.pdftools.operations.shared.coordinates.VisualPageSpace;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

final class RenderedPageComparator {

    private static final int WHITE = 0x00ffffff;
    private static final int DIFFERENCE = 0x00e5484d;

    private final CompareProperties properties;
    private final ComparePlanFactory.ComparePlan plan;
    private long totalRenderPixels;
    private long totalImageBytes;

    RenderedPageComparator(
            CompareProperties properties,
            ComparePlanFactory.ComparePlan plan) {
        this.properties = properties;
        this.plan = plan;
    }

    CompareReport.VisualComparison compare(
            PDFRenderer baselineRenderer,
            PDDocument baselineDocument,
            int baselineIndex,
            PDFRenderer candidateRenderer,
            PDDocument candidateDocument,
            int candidateIndex,
            Path workspace,
            int pageNumber) {
        BufferedImage baseline = null;
        BufferedImage candidate = null;
        BufferedImage diff = null;
        Path output = workspace.resolve(
            "page-%03d-diff.png".formatted(pageNumber)
        );
        try {
            baseline = render(
                baselineRenderer,
                baselineDocument,
                baselineIndex,
                pageNumber
            );
            candidate = render(
                candidateRenderer,
                candidateDocument,
                candidateIndex,
                pageNumber
            );
            int width = Math.max(width(baseline), width(candidate));
            int height = Math.max(height(baseline), height(candidate));
            long totalPixels = pixels(width, height, pageNumber);
            addRenderPixels(totalPixels);
            int[] baselineRow = new int[width];
            int[] candidateRow = new int[width];
            int[] diffRow = new int[width];
            diff = new BufferedImage(
                width,
                height,
                BufferedImage.TYPE_INT_RGB
            );
            long differentPixels = 0;
            for (int y = 0; y < height; y++) {
                row(baseline, y, baselineRow);
                row(candidate, y, candidateRow);
                for (int x = 0; x < width; x++) {
                    if (different(
                            baselineRow[x],
                            candidateRow[x])) {
                        differentPixels++;
                        diffRow[x] = DIFFERENCE;
                    } else {
                        diffRow[x] = faded(baselineRow[x]);
                    }
                }
                diff.setRGB(0, y, width, 1, diffRow, 0, width);
            }
            if (differentPixels == 0) {
                return new CompareReport.VisualComparison(
                    false,
                    0,
                    totalPixels,
                    0,
                    null
                );
            }
            writeDiff(diff, output);
            long imageBytes = Files.size(output);
            totalImageBytes = Math.addExact(
                totalImageBytes,
                imageBytes
            );
            if (totalImageBytes
                    > properties.getMaxTotalDiffImageBytes()) {
                throw new OperationException(
                    "COMPARE_DIFF_IMAGE_LIMIT_EXCEEDED",
                    "The visual comparison images exceed their total limit"
                );
            }
            return new CompareReport.VisualComparison(
                true,
                differentPixels,
                totalPixels,
                roundedPercent(differentPixels, totalPixels),
                "visual/" + output.getFileName()
            );
        } catch (OperationException exception) {
            delete(output, exception);
            throw exception;
        } catch (IOException | ArithmeticException exception) {
            OperationException failure = new OperationException(
                "COMPARE_RENDER_FAILED",
                "A PDF page could not be compared visually",
                exception
            );
            delete(output, failure);
            throw failure;
        } finally {
            if (baseline != null) {
                baseline.flush();
            }
            if (candidate != null) {
                candidate.flush();
            }
            if (diff != null) {
                diff.flush();
            }
        }
    }

    private BufferedImage render(
            PDFRenderer renderer,
            PDDocument document,
            int pageIndex,
            int pageNumber) throws IOException {
        if (pageIndex < 0) {
            return null;
        }
        PDPage page = document.getPage(pageIndex);
        VisualPageSpace space = VisualPageSpace.from(page);
        double scale = plan.renderDpi() / 72.0 * space.userUnit();
        int width = Math.max(
            1,
            (int) Math.ceil(space.width() * scale)
        );
        int height = Math.max(
            1,
            (int) Math.ceil(space.height() * scale)
        );
        pixels(width, height, pageNumber);
        BufferedImage image = renderer.renderImage(
            pageIndex,
            (float) scale,
            ImageType.RGB
        );
        pixels(image.getWidth(), image.getHeight(), pageNumber);
        addRenderPixels(
            Math.multiplyExact(
                (long) image.getWidth(),
                image.getHeight()
            )
        );
        return image;
    }

    private long pixels(int width, int height, int pageNumber) {
        long pixels;
        try {
            pixels = Math.multiplyExact((long) width, height);
        } catch (ArithmeticException exception) {
            throw renderLimit(pageNumber);
        }
        if (width < 1
                || height < 1
                || width > properties.getMaxImageDimension()
                || height > properties.getMaxImageDimension()
                || pixels > properties.getMaxPixelsPerPage()) {
            throw renderLimit(pageNumber);
        }
        return pixels;
    }

    private void addRenderPixels(long pixels) {
        try {
            totalRenderPixels = Math.addExact(
                totalRenderPixels,
                pixels
            );
        } catch (ArithmeticException exception) {
            throw totalRenderLimit();
        }
        if (totalRenderPixels > properties.getMaxTotalRenderPixels()) {
            throw totalRenderLimit();
        }
    }

    private void row(
            BufferedImage image,
            int y,
            int[] destination) {
        java.util.Arrays.fill(destination, WHITE);
        if (image == null || y >= image.getHeight()) {
            return;
        }
        image.getRGB(
            0,
            y,
            image.getWidth(),
            1,
            destination,
            0,
            destination.length
        );
        for (int index = 0; index < image.getWidth(); index++) {
            destination[index] &= WHITE;
        }
    }

    private boolean different(int baseline, int candidate) {
        int red = Math.abs(
            (baseline >> 16 & 0xff) - (candidate >> 16 & 0xff)
        );
        int green = Math.abs(
            (baseline >> 8 & 0xff) - (candidate >> 8 & 0xff)
        );
        int blue = Math.abs(
            (baseline & 0xff) - (candidate & 0xff)
        );
        return Math.max(red, Math.max(green, blue))
            > plan.pixelTolerance();
    }

    private int faded(int rgb) {
        int red = 224 + (rgb >> 16 & 0xff) / 8;
        int green = 224 + (rgb >> 8 & 0xff) / 8;
        int blue = 224 + (rgb & 0xff) / 8;
        return Math.min(red, 255) << 16
            | Math.min(green, 255) << 8
            | Math.min(blue, 255);
    }

    private void writeDiff(
            BufferedImage image,
            Path output) throws IOException {
        try (OutputStream fileOutput = Files.newOutputStream(output);
             BoundedOutputStream bounded = new BoundedOutputStream(
                 fileOutput,
                 properties.getMaxDiffImageBytes(),
                 () -> {
                 }
             )) {
            if (!ImageIO.write(image, "png", bounded)) {
                throw new OperationException(
                    "COMPARE_PNG_WRITER_UNAVAILABLE",
                    "No PNG writer is available for visual comparison"
                );
            }
        } catch (OutputLimitExceededException exception) {
            throw new OperationException(
                "COMPARE_DIFF_IMAGE_LIMIT_EXCEEDED",
                "A visual comparison image exceeds its size limit",
                exception
            );
        }
    }

    private double roundedPercent(long difference, long total) {
        return Math.round(
            100_000_000.0 * difference / total
        ) / 1_000_000.0;
    }

    private int width(BufferedImage image) {
        return image == null ? 1 : image.getWidth();
    }

    private int height(BufferedImage image) {
        return image == null ? 1 : image.getHeight();
    }

    private OperationException renderLimit(int pageNumber) {
        return new OperationException(
            "COMPARE_RENDER_LIMIT_EXCEEDED",
            "Page " + pageNumber
                + " exceeds the visual comparison pixel limit"
        );
    }

    private OperationException totalRenderLimit() {
        return new OperationException(
            "COMPARE_TOTAL_RENDER_LIMIT_EXCEEDED",
            "The PDFs exceed the aggregate comparison pixel limit"
        );
    }

    private void delete(Path path, RuntimeException failure) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            failure.addSuppressed(exception);
        }
    }
}
