package com.pdftools.operations.redact;

import com.pdftools.operations.BoundedOutputStream;
import com.pdftools.operations.OperationException;
import com.pdftools.operations.OutputLimitExceededException;
import com.pdftools.operations.shared.coordinates.VisualPageSpace;
import com.pdftools.operations.shared.pdf.PdfInputValidator;
import com.pdftools.operations.shared.pdf.PdfPageTreeReader;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.io.RandomAccessReadBufferedFile;
import org.apache.pdfbox.io.RandomAccessStreamCache;
import org.apache.pdfbox.io.ScratchFile;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.FileCacheImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class RedactWorker {

    private static final Logger logger =
        LoggerFactory.getLogger(RedactWorker.class);

    private final RedactWorkerRequest request;
    private final PdfPageTreeReader pageTreeReader;

    RedactWorker(RedactWorkerRequest request) {
        this.request = request;
        this.pageTreeReader = new PdfPageTreeReader(request);
    }

    void redact(Path progressFile) {
        PdfInputValidator.requirePdfHeader(request.source());
        validateStaticRequest();
        Path sourceScratch = request.output().resolveSibling(
            ".redact-source-scratch"
        );
        Path outputScratch = request.output().resolveSibling(
            ".redact-output-scratch"
        );
        Path imageCache = request.output().resolveSibling(
            ".redact-image-cache"
        );
        Path pageImage = request.output().resolveSibling(
            ".redact-page.jpg"
        );
        Path temporaryOutput = request.output().resolveSibling(
            request.output().getFileName() + ".tmp"
        );
        createDirectories(sourceScratch, outputScratch, imageCache);
        RandomAccessStreamCache.StreamCacheCreateFunction sourceCache =
            () -> new ScratchFile(sourceScratch.toFile());
        RandomAccessStreamCache.StreamCacheCreateFunction outputCache =
            () -> new ScratchFile(outputScratch.toFile());
        RuntimeException failure = null;
        try (RandomAccessReadBufferedFile randomAccess =
                 new RandomAccessReadBufferedFile(request.source());
             PDDocument source = Loader.loadPDF(randomAccess, sourceCache);
             PDDocument output = new PDDocument(outputCache)) {
            if (source.isEncrypted()) {
                throw encryptedPdf();
            }
            PdfPageTreeReader.Result pageTree = pageTreeReader.read(
                source,
                () -> {
                }
            );
            validateAreas(pageTree.pages().size());
            List<PageRenderPlan> pages = renderPlans(pageTree.pages());
            PDFRenderer renderer = new PDFRenderer(source);
            renderer.setSubsamplingAllowed(true);
            long totalImageBytes = 0;
            for (int index = 0; index < pages.size(); index++) {
                PageRenderPlan page = pages.get(index);
                BufferedImage image = null;
                try {
                    image = renderer.renderImage(
                        index,
                        page.scale(),
                        ImageType.RGB
                    );
                    applyRedactions(
                        image,
                        areasForPage(index + 1)
                    );
                    writeJpeg(image, pageImage, imageCache);
                    totalImageBytes = addImageBytes(
                        totalImageBytes,
                        Files.size(pageImage)
                    );
                    addRasterPage(output, page, pageImage);
                } finally {
                    if (image != null) {
                        image.flush();
                    }
                    delete(pageImage, null);
                }
                publishProgress(
                    progressFile,
                    (int) Math.floor(100.0 * (index + 1) / pages.size())
                );
            }
            output.setDocumentInformation(new PDDocumentInformation());
            setDeterministicId(output);
            save(output, temporaryOutput);
            moveAtomically(temporaryOutput, request.output());
        } catch (InvalidPasswordException exception) {
            failure = encryptedPdf();
            throw failure;
        } catch (OperationException exception) {
            failure = exception;
            throw exception;
        } catch (IOException | ArithmeticException exception) {
            failure = new OperationException(
                "REDACT_FAILED",
                "The PDF could not be securely redacted",
                exception
            );
            throw failure;
        } finally {
            delete(pageImage, failure);
            delete(temporaryOutput, failure);
        }
    }

    private void validateStaticRequest() {
        if (request.maxAreas() < 1
                || request.maxAreasPerPage() < 1
                || request.maxDocumentPages() < 1
                || request.maxPageTreeNodes() < 1
                || request.maxPageTreeDepth() < 1
                || request.maxContentStreamsPerPage() < 1
                || request.renderDpi() < 72
                || request.renderDpi() > 600
                || request.jpegQuality() < 10
                || request.jpegQuality() > 100
                || request.maxPixelsPerPage() < 1
                || request.maxImageDimension() < 1
                || request.maxImageBytes() < 1
                || request.maxTotalImageBytes() < 1
                || request.maxOutputBytes() < 1
                || request.sourceSha256().isBlank()
                || request.sourceSha256().length() > 128
                || request.source().equals(request.output())
                || Files.exists(
                    request.output(),
                    java.nio.file.LinkOption.NOFOLLOW_LINKS
                )) {
            throw protocolFailure();
        }
    }

    private void validateAreas(int pageCount) {
        if (request.areas().isEmpty()
                || request.areas().size() > request.maxAreas()) {
            throw protocolFailure();
        }
        Set<RedactPlanFactory.RedactArea> unique = new HashSet<>();
        Map<Integer, Integer> pageCounts = new HashMap<>();
        for (RedactPlanFactory.RedactArea area : request.areas()) {
            if (!validArea(area)
                    || area.page() > pageCount
                    || !unique.add(area)) {
                throw area.page() > pageCount
                    ? new OperationException(
                        "REDACT_PAGE_OUT_OF_RANGE",
                        "Redaction page " + area.page()
                            + " exceeds the document page count"
                    )
                    : protocolFailure();
            }
            int count = pageCounts.merge(area.page(), 1, Integer::sum);
            if (count > request.maxAreasPerPage()) {
                throw protocolFailure();
            }
        }
    }

    private boolean validArea(RedactPlanFactory.RedactArea area) {
        return area.page() > 0
            && finiteBounded(area.x())
            && finiteBounded(area.y())
            && Float.isFinite(area.width())
            && Float.isFinite(area.height())
            && area.width() > 0
            && area.height() > 0
            && area.x() + area.width() <= 1.000001f
            && area.y() + area.height() <= 1.000001f;
    }

    private boolean finiteBounded(float value) {
        return Float.isFinite(value) && value >= 0 && value <= 1;
    }

    private List<PageRenderPlan> renderPlans(List<PDPage> pages) {
        return pages.stream().map(page -> {
            VisualPageSpace space = VisualPageSpace.from(page);
            double scale = request.renderDpi() / 72.0 * space.userUnit();
            long width = Math.max(
                (long) Math.floor(space.width() * scale),
                1
            );
            long height = Math.max(
                (long) Math.floor(space.height() * scale),
                1
            );
            long pixels;
            try {
                pixels = Math.multiplyExact(width, height);
            } catch (ArithmeticException exception) {
                throw renderSizeLimit();
            }
            if (width > request.maxImageDimension()
                    || height > request.maxImageDimension()
                    || pixels > request.maxPixelsPerPage()) {
                throw renderSizeLimit();
            }
            return new PageRenderPlan(
                (float) scale,
                space.width() * space.userUnit(),
                space.height() * space.userUnit()
            );
        }).toList();
    }

    private List<RedactPlanFactory.RedactArea> areasForPage(int page) {
        return request.areas().stream()
            .filter(area -> area.page() == page)
            .toList();
    }

    private void applyRedactions(
            BufferedImage image,
            List<RedactPlanFactory.RedactArea> areas) {
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.BLACK);
            for (RedactPlanFactory.RedactArea area : areas) {
                int left = Math.max(
                    (int) Math.floor(area.x() * image.getWidth()),
                    0
                );
                int top = Math.max(
                    (int) Math.floor(area.y() * image.getHeight()),
                    0
                );
                int right = Math.min(
                    (int) Math.ceil(
                        (area.x() + area.width()) * image.getWidth()
                    ),
                    image.getWidth()
                );
                int bottom = Math.min(
                    (int) Math.ceil(
                        (area.y() + area.height()) * image.getHeight()
                    ),
                    image.getHeight()
                );
                graphics.fillRect(
                    left,
                    top,
                    Math.max(right - left, 1),
                    Math.max(bottom - top, 1)
                );
            }
        } finally {
            graphics.dispose();
        }
    }

    private void writeJpeg(
            BufferedImage image,
            Path output,
            Path cacheDirectory) throws IOException {
        Iterator<ImageWriter> writers =
            ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            throw new OperationException(
                "REDACT_JPEG_WRITER_UNAVAILABLE",
                "Secure redaction requires a JPEG image writer"
            );
        }
        ImageWriter writer = writers.next();
        try (OutputStream fileOutput = Files.newOutputStream(output);
             BoundedOutputStream bounded = new BoundedOutputStream(
                 fileOutput,
                 request.maxImageBytes(),
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
                    "REDACT_JPEG_QUALITY_UNSUPPORTED",
                    "The JPEG writer cannot apply redaction quality"
                );
            }
            parameters.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            parameters.setCompressionQuality(request.jpegQuality() / 100f);
            writer.write(null, new IIOImage(image, null, null), parameters);
            imageOutput.flush();
        } catch (OutputLimitExceededException exception) {
            throw new OperationException(
                "REDACT_IMAGE_SIZE_LIMIT_EXCEEDED",
                "A redacted page exceeds the configured image limit",
                exception
            );
        } finally {
            writer.dispose();
        }
    }

    private long addImageBytes(long current, long next) {
        long total;
        try {
            total = Math.addExact(current, next);
        } catch (ArithmeticException exception) {
            throw totalImageSizeLimit();
        }
        if (total > request.maxTotalImageBytes()) {
            throw totalImageSizeLimit();
        }
        return total;
    }

    private void addRasterPage(
            PDDocument document,
            PageRenderPlan plan,
            Path imagePath) throws IOException {
        PDPage page = new PDPage(new PDRectangle(
            plan.pageWidth(),
            plan.pageHeight()
        ));
        document.addPage(page);
        try (InputStream input = Files.newInputStream(imagePath);
             PDPageContentStream content =
                 new PDPageContentStream(document, page)) {
            PDImageXObject image = JPEGFactory.createFromStream(
                document,
                input
            );
            content.drawImage(
                image,
                0,
                0,
                plan.pageWidth(),
                plan.pageHeight()
            );
        }
    }

    private void setDeterministicId(PDDocument document) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                "SHA-256 is unavailable",
                exception
            );
        }
        digest.update(
            request.sourceSha256().getBytes(StandardCharsets.US_ASCII)
        );
        digest.update(ByteBuffer.allocate(Integer.BYTES)
            .putInt(request.renderDpi())
            .array());
        digest.update(ByteBuffer.allocate(Integer.BYTES)
            .putInt(request.jpegQuality())
            .array());
        for (RedactPlanFactory.RedactArea area : request.areas()) {
            digest.update(ByteBuffer.allocate(Integer.BYTES)
                .putInt(area.page())
                .array());
            digest.update(ByteBuffer.allocate(Float.BYTES * 4)
                .putFloat(area.x())
                .putFloat(area.y())
                .putFloat(area.width())
                .putFloat(area.height())
                .array());
        }
        byte[] id = digest.digest();
        COSArray ids = new COSArray();
        ids.add(new COSString(id));
        ids.add(new COSString(id));
        document.getDocument().setDocumentID(ids);
    }

    private void save(PDDocument document, Path output) throws IOException {
        try (OutputStream fileOutput = Files.newOutputStream(output);
             BoundedOutputStream bounded = new BoundedOutputStream(
                 fileOutput,
                 request.maxOutputBytes(),
                 () -> {
                 }
             )) {
            document.save(bounded);
        } catch (OutputLimitExceededException exception) {
            throw new OperationException(
                "REDACT_OUTPUT_SIZE_LIMIT_EXCEEDED",
                "The redacted PDF exceeds the configured output limit",
                exception
            );
        }
    }

    private void publishProgress(Path progressFile, int completed)
            throws IOException {
        Path temporary = progressFile.resolveSibling(
            progressFile.getFileName() + ".tmp"
        );
        Files.writeString(temporary, Integer.toString(completed));
        moveAtomically(temporary, progressFile);
    }

    private void moveAtomically(Path source, Path destination)
            throws IOException {
        try {
            Files.move(
                source,
                destination,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            );
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(
                source,
                destination,
                StandardCopyOption.REPLACE_EXISTING
            );
        }
    }

    private void createDirectories(Path... directories) {
        try {
            for (Path directory : directories) {
                Files.createDirectories(directory);
            }
        } catch (IOException exception) {
            throw new OperationException(
                "REDACT_SCRATCH_FAILED",
                "Secure redaction scratch storage could not be created",
                exception
            );
        }
    }

    private void delete(Path path, RuntimeException failure) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            OperationException cleanupFailure = new OperationException(
                "REDACT_CLEANUP_FAILED",
                "Secure redaction scratch could not be removed",
                exception
            );
            if (failure != null) {
                failure.addSuppressed(cleanupFailure);
            }
            logger.error(
                "Could not remove secure redaction scratch {}",
                path,
                cleanupFailure
            );
        }
    }

    private OperationException encryptedPdf() {
        return new OperationException(
            "ENCRYPTED_PDF",
            "Unlock the PDF before redacting it"
        );
    }

    private OperationException protocolFailure() {
        return new OperationException(
            "REDACT_WORKER_PROTOCOL_ERROR",
            "The secure redaction worker received invalid controls"
        );
    }

    private OperationException renderSizeLimit() {
        return new OperationException(
            "REDACT_RENDER_SIZE_LIMIT_EXCEEDED",
            "A PDF page exceeds the secure redaction render limit"
        );
    }

    private OperationException totalImageSizeLimit() {
        return new OperationException(
            "REDACT_TOTAL_IMAGE_SIZE_LIMIT_EXCEEDED",
            "Redacted page images exceed the configured total limit"
        );
    }

    private record PageRenderPlan(
        float scale,
        float pageWidth,
        float pageHeight
    ) {
    }
}
