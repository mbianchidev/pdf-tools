package com.pdftools.operations.pdfjpg;

import com.pdftools.operations.BoundedOutputStream;
import com.pdftools.operations.OperationException;
import com.pdftools.operations.OutputLimitExceededException;
import com.pdftools.operations.shared.pdf.PdfInputValidator;
import com.pdftools.operations.shared.pdf.PdfPageTreeReader;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBufferedFile;
import org.apache.pdfbox.io.RandomAccessStreamCache;
import org.apache.pdfbox.io.ScratchFile;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.FileCacheImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

final class PdfToJpgWorker {

    private static final Logger logger =
        LoggerFactory.getLogger(PdfToJpgWorker.class);

    private final PdfToJpgProperties properties;
    private final PdfPageTreeReader pageTreeReader;

    PdfToJpgWorker(PdfToJpgProperties properties) {
        this.properties = properties;
        this.pageTreeReader = new PdfPageTreeReader(properties);
    }

    void render(
            Path source,
            Path workspace,
            Path progressFile,
            List<Integer> selectedPages,
            int dpi,
            int quality) {
        PdfInputValidator.requirePdfHeader(source);
        Path scratchDirectory = workspace.resolve(".pdfbox-scratch");
        Path imageCacheDirectory = workspace.resolve(".imageio-cache");
        createDirectories(scratchDirectory, imageCacheDirectory);
        RandomAccessStreamCache.StreamCacheCreateFunction scratchCache =
            () -> new ScratchFile(scratchDirectory.toFile());
        List<Path> outputs = new ArrayList<>();
        long totalBytes = 0;
        try (RandomAccessReadBufferedFile randomAccess =
                 new RandomAccessReadBufferedFile(source);
             PDDocument document = Loader.loadPDF(
                 randomAccess,
                 scratchCache
             )) {
            if (document.isEncrypted()) {
                throw encryptedPdf();
            }
            PdfPageTreeReader.Result pageTree = pageTreeReader.read(
                document,
                () -> {
                }
            );
            validateControls(selectedPages, pageTree.pages().size(), dpi, quality);
            validateRenderSizes(pageTree.pages(), selectedPages, dpi);
            PDFRenderer renderer = new PDFRenderer(document);
            renderer.setSubsamplingAllowed(true);

            for (int index = 0; index < selectedPages.size(); index++) {
                int pageNumber = selectedPages.get(index);
                Path output = outputPath(workspace, pageNumber);
                renderPage(
                    renderer,
                    pageTree.pages().get(pageNumber - 1),
                    pageNumber,
                    dpi,
                    quality,
                    output,
                    imageCacheDirectory
                );
                outputs.add(output);
                totalBytes = addTotalBytes(
                    totalBytes,
                    Files.size(output)
                );
                publishProgress(progressFile, index + 1);
            }
        } catch (InvalidPasswordException exception) {
            OperationException failure = encryptedPdf();
            cleanup(outputs, failure);
            throw failure;
        } catch (OperationException exception) {
            cleanup(outputs, exception);
            throw exception;
        } catch (IOException exception) {
            OperationException failure = new OperationException(
                "JPG_RENDER_FAILED",
                "The PDF could not be rendered to JPG",
                exception
            );
            cleanup(outputs, failure);
            throw failure;
        }
    }

    private void validateControls(
            List<Integer> pages,
            int pageCount,
            int dpi,
            int quality) {
        if (pages.isEmpty()
                || pages.size() > properties.getMaxSelectedPages()) {
            throw new OperationException(
                "JPG_PAGE_SELECTION_LIMIT_EXCEEDED",
                "The isolated renderer received an invalid page selection"
            );
        }
        int previous = 0;
        for (int page : pages) {
            if (page <= previous || page > pageCount) {
                throw new OperationException(
                    "INVALID_JPG_PAGES",
                    "The isolated renderer received invalid page numbers"
                );
            }
            previous = page;
        }
        if (dpi < properties.getMinDpi()
                || dpi > properties.getMaxDpi()
                || quality < 10
                || quality > 100) {
            throw new OperationException(
                "INVALID_JPG_CONTROLS",
                "The isolated renderer received invalid image controls"
            );
        }
    }

    private void validateRenderSizes(
            List<PDPage> pages,
            List<Integer> selectedPages,
            int dpi) {
        for (int pageNumber : selectedPages) {
            PDPage page = pages.get(pageNumber - 1);
            double userUnit = page.getUserUnit();
            PDRectangle cropBox = page.getCropBox();
            double widthPoints = cropBox.getWidth();
            double heightPoints = cropBox.getHeight();
            if (!Double.isFinite(userUnit)
                    || userUnit <= 0
                    || !Double.isFinite(widthPoints)
                    || !Double.isFinite(heightPoints)
                    || widthPoints <= 0
                    || heightPoints <= 0) {
                throw new OperationException(
                    "INVALID_PDF_PAGE_SIZE",
                    "Page " + pageNumber + " has invalid dimensions"
                );
            }
            double scale = dpi / 72.0 * userUnit;
            long width = Math.max(
                (long) Math.floor(widthPoints * scale),
                1
            );
            long height = Math.max(
                (long) Math.floor(heightPoints * scale),
                1
            );
            long pixels;
            try {
                pixels = Math.multiplyExact(width, height);
            } catch (ArithmeticException exception) {
                throw renderSizeLimit(pageNumber);
            }
            if (width > properties.getMaxImageDimension()
                    || height > properties.getMaxImageDimension()
                    || pixels > properties.getMaxPixelsPerPage()) {
                throw renderSizeLimit(pageNumber);
            }
        }
    }

    private void renderPage(
            PDFRenderer renderer,
            PDPage page,
            int pageNumber,
            int dpi,
            int quality,
            Path output,
            Path cacheDirectory) {
        BufferedImage image = null;
        try {
            float scale = (float) (dpi / 72.0 * page.getUserUnit());
            image = renderer.renderImage(
                pageNumber - 1,
                scale,
                ImageType.RGB
            );
            writeJpeg(image, output, cacheDirectory, quality);
        } catch (OutputLimitExceededException exception) {
            throw cleanup(output, new OperationException(
                "JPG_IMAGE_SIZE_LIMIT_EXCEEDED",
                "A rendered JPG exceeds the configured image limit"
            ));
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof OperationException operationException) {
                throw cleanup(output, operationException);
            }
            throw cleanup(output, new OperationException(
                "JPG_PAGE_RENDER_FAILED",
                "Page " + pageNumber + " could not be rendered to JPG",
                exception
            ));
        } finally {
            if (image != null) {
                image.flush();
            }
        }
    }

    private void writeJpeg(
            BufferedImage image,
            Path output,
            Path cacheDirectory,
            int quality) throws IOException {
        Iterator<ImageWriter> writers =
            ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            throw new OperationException(
                "JPG_WRITER_UNAVAILABLE",
                "No JPEG image writer is available"
            );
        }
        ImageWriter writer = writers.next();
        try (OutputStream fileOutput = Files.newOutputStream(output);
             BoundedOutputStream bounded = new BoundedOutputStream(
                 fileOutput,
                 properties.getMaxImageBytes(),
                 () -> {
                 }
             );
             FileCacheImageOutputStream imageOutput =
                 new FileCacheImageOutputStream(
                     bounded,
                     cacheDirectory.toFile()
                 )) {
            writer.setOutput(imageOutput);
            ImageWriteParam parameters = writer.getDefaultWriteParam();
            if (!parameters.canWriteCompressed()) {
                throw new OperationException(
                    "JPG_QUALITY_UNSUPPORTED",
                    "The JPEG writer does not support quality controls"
                );
            }
            parameters.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            parameters.setCompressionQuality(quality / 100f);
            writer.write(null, new IIOImage(image, null, null), parameters);
            imageOutput.flush();
        } finally {
            writer.dispose();
        }
    }

    private long addTotalBytes(long current, long imageBytes) {
        long total;
        try {
            total = Math.addExact(current, imageBytes);
        } catch (ArithmeticException exception) {
            throw totalSizeLimit();
        }
        if (total > properties.getMaxTotalImageBytes()) {
            throw totalSizeLimit();
        }
        return total;
    }

    private void publishProgress(Path progressFile, int completed)
            throws IOException {
        Path temporary = progressFile.resolveSibling(
            progressFile.getFileName() + ".tmp"
        );
        Files.writeString(temporary, Integer.toString(completed));
        try {
            Files.move(
                temporary,
                progressFile,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            );
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(
                temporary,
                progressFile,
                StandardCopyOption.REPLACE_EXISTING
            );
        }
    }

    private Path outputPath(Path workspace, int pageNumber) {
        return workspace.resolve(String.format(
            Locale.ROOT,
            "page-%04d.jpg",
            pageNumber
        ));
    }

    private void createDirectories(Path... directories) {
        try {
            for (Path directory : directories) {
                Files.createDirectories(directory);
            }
        } catch (IOException exception) {
            throw new OperationException(
                "JPG_SCRATCH_FAILED",
                "PDF-to-JPG scratch storage could not be created",
                exception
            );
        }
    }

    private OperationException encryptedPdf() {
        return new OperationException(
            "ENCRYPTED_PDF",
            "Unlock the PDF before converting it to JPG"
        );
    }

    private OperationException renderSizeLimit(int pageNumber) {
        return new OperationException(
            "JPG_RENDER_SIZE_LIMIT_EXCEEDED",
            "Page " + pageNumber
                + " exceeds the configured render dimensions"
        );
    }

    private OperationException totalSizeLimit() {
        return new OperationException(
            "JPG_TOTAL_SIZE_LIMIT_EXCEEDED",
            "Rendered JPG files exceed the configured total size limit"
        );
    }

    private void cleanup(List<Path> outputs, RuntimeException failure) {
        outputs.forEach(output -> cleanup(output, failure));
    }

    private <T extends RuntimeException> T cleanup(
            Path output,
            T failure) {
        try {
            Files.deleteIfExists(output);
        } catch (IOException exception) {
            OperationException cleanupFailure = new OperationException(
                "JPG_CLEANUP_FAILED",
                "Partial JPG output could not be removed",
                exception
            );
            failure.addSuppressed(cleanupFailure);
            logger.error(
                "Could not remove partial JPG output {}",
                output,
                cleanupFailure
            );
        }
        return failure;
    }
}
