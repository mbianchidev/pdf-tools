package com.pdftools.operations.compress;

import com.pdftools.operations.BoundedOutputStream;
import com.pdftools.operations.OperationException;
import com.pdftools.operations.OutputLimitExceededException;
import com.pdftools.operations.shared.pdf.PdfInputValidator;
import com.pdftools.operations.shared.pdf.PdfPageTreeReader;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.io.RandomAccessReadBufferedFile;
import org.apache.pdfbox.io.RandomAccessStreamCache;
import org.apache.pdfbox.io.ScratchFile;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdfwriter.compress.CompressParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.FileCacheImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class CompressPdfWorker {

    private static final Logger logger =
        LoggerFactory.getLogger(CompressPdfWorker.class);

    private final CompressPdfRequest request;
    private final CompressPdfProperties properties;
    private final Set<COSBase> visitedResources =
        Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<COSBase, PDImageXObject> processedImages =
        new IdentityHashMap<>();
    private int images;
    private int xObjects;
    private long totalImagePixels;
    private long totalRecompressedImageBytes;

    CompressPdfWorker(CompressPdfRequest request) {
        this.request = request;
        this.properties = request.properties();
    }

    void compress(Path progressFile) {
        PdfInputValidator.requirePdfHeader(request.source());
        validateStaticRequest();
        Path sourceScratch = request.workspace().resolve(
            ".compress-source-scratch"
        );
        Path imageCache = request.workspace().resolve(
            ".compress-image-cache"
        );
        Path imageFile = request.workspace().resolve(
            ".compress-image.jpg"
        );
        Path candidate = request.workspace().resolve(
            ".compress-candidate.pdf"
        );
        Path temporaryOutput = request.workspace().resolve(
            ".compress-output.pdf.tmp"
        );
        createDirectories(sourceScratch, imageCache);
        RandomAccessStreamCache.StreamCacheCreateFunction scratchCache =
            () -> new ScratchFile(sourceScratch.toFile());
        RuntimeException failure = null;
        try (RandomAccessReadBufferedFile randomAccess =
                 new RandomAccessReadBufferedFile(request.source());
             PDDocument document = Loader.loadPDF(
                 randomAccess,
                 scratchCache
             )) {
            if (document.isEncrypted()) {
                throw encryptedPdf(null);
            }
            List<PDPage> pages = new PdfPageTreeReader(properties).read(
                document,
                () -> {
                }
            ).pages();
            if (pages.isEmpty()) {
                throw invalidPdf(null);
            }
            if (request.mode()
                    == CompressPdfPlanFactory.CompressionMode.LOW) {
                writeProgress(progressFile, pages.size());
            } else {
                for (int index = 0; index < pages.size(); index++) {
                    processResources(
                        document,
                        pages.get(index).getResources(),
                        0,
                        imageFile,
                        imageCache
                    );
                    writeProgress(progressFile, index + 1);
                }
            }
            setDeterministicId(document);
            boolean candidateSaved = saveCandidate(document, candidate);
            if (candidateSaved
                    && Files.size(candidate)
                        < Files.size(request.source())) {
                moveAtomically(candidate, temporaryOutput);
            } else {
                delete(candidate, null);
                copySource(temporaryOutput);
            }
            moveAtomically(temporaryOutput, request.output());
        } catch (InvalidPasswordException exception) {
            failure = encryptedPdf(exception);
            throw failure;
        } catch (OperationException exception) {
            failure = exception;
            throw exception;
        } catch (IOException | ArithmeticException exception) {
            failure = new OperationException(
                "PDF_COMPRESSION_FAILED",
                "The PDF could not be compressed",
                exception
            );
            throw failure;
        } finally {
            delete(imageFile, failure);
            delete(candidate, failure);
            delete(temporaryOutput, failure);
        }
    }

    private void processResources(
            PDDocument document,
            PDResources resources,
            int depth,
            Path imageFile,
            Path imageCache) throws IOException {
        if (resources == null
                || !visitedResources.add(resources.getCOSObject())) {
            return;
        }
        if (depth > properties.getMaxResourceDepth()) {
            throw new OperationException(
                "COMPRESS_RESOURCE_LIMIT_EXCEEDED",
                "The PDF resource graph exceeds the compression limit"
            );
        }
        List<COSName> names = new ArrayList<>();
        for (COSName name : resources.getXObjectNames()) {
            xObjects++;
            if (xObjects > properties.getMaxXObjects()) {
                throw new OperationException(
                    "COMPRESS_XOBJECT_LIMIT_EXCEEDED",
                    "The PDF contains too many image or form resources"
                );
            }
            names.add(name);
        }
        for (COSName name : names) {
            PDXObject xObject = resources.getXObject(name);
            if (xObject instanceof PDImageXObject image) {
                PDImageXObject replacement = replacement(
                    document,
                    image,
                    imageFile,
                    imageCache
                );
                if (replacement != image) {
                    resources.put(name, replacement);
                }
            } else if (xObject instanceof PDFormXObject form) {
                processResources(
                    document,
                    form.getResources(),
                    depth + 1,
                    imageFile,
                    imageCache
                );
            }
        }
    }

    private PDImageXObject replacement(
            PDDocument document,
            PDImageXObject image,
            Path imageFile,
            Path imageCache) throws IOException {
        COSBase key = image.getCOSObject();
        if (processedImages.containsKey(key)) {
            return processedImages.get(key);
        }
        images++;
        if (images > properties.getMaxImages()) {
            throw new OperationException(
                "COMPRESS_IMAGE_COUNT_LIMIT_EXCEEDED",
                "The PDF contains too many images to compress safely"
            );
        }
        validateImageDimensions(image);
        PDImageXObject replacement = recompress(
            document,
            image,
            imageFile,
            imageCache
        );
        processedImages.put(key, replacement);
        return replacement;
    }

    private void validateImageDimensions(PDImageXObject image) {
        int width = image.getWidth();
        int height = image.getHeight();
        long pixels;
        try {
            pixels = Math.multiplyExact((long) width, height);
            totalImagePixels = Math.addExact(
                totalImagePixels,
                pixels
            );
        } catch (ArithmeticException exception) {
            throw imageLimit();
        }
        if (width < 1
                || height < 1
                || width > properties.getMaxImageDimension()
                || height > properties.getMaxImageDimension()
                || pixels > properties.getMaxPixelsPerImage()
                || totalImagePixels
                    > properties.getMaxTotalImagePixels()) {
            throw imageLimit();
        }
    }

    private PDImageXObject recompress(
            PDDocument document,
            PDImageXObject image,
            Path imageFile,
            Path imageCache) throws IOException {
        if (image.isStencil()
                || image.getCOSObject().containsKey(COSName.MASK)
                || image.getCOSObject().containsKey(COSName.SMASK)) {
            return image;
        }
        BufferedImage decoded;
        try {
            decoded = image.getImage();
        } catch (IOException exception) {
            logger.warn(
                "Keeping image {} unchanged because it cannot be decoded",
                images,
                exception
            );
            return image;
        }
        if (decoded == null || decoded.getColorModel().hasAlpha()) {
            if (decoded != null) {
                decoded.flush();
            }
            return image;
        }
        BufferedImage prepared = null;
        try {
            prepared = prepareImage(decoded);
            writeJpeg(prepared, imageFile, imageCache);
            long candidateBytes = Files.size(imageFile);
            try {
                totalRecompressedImageBytes = Math.addExact(
                    totalRecompressedImageBytes,
                    candidateBytes
                );
            } catch (ArithmeticException exception) {
                throw recompressedImageLimit();
            }
            if (totalRecompressedImageBytes
                    > properties.getMaxTotalRecompressedImageBytes()) {
                throw recompressedImageLimit();
            }
            if (image.getCOSObject().getLength() > 0
                        && candidateBytes
                            >= image.getCOSObject().getLength()) {
                return image;
            }
            try (InputStream input = Files.newInputStream(imageFile)) {
                PDImageXObject replacement =
                    JPEGFactory.createFromStream(document, input);
                replacement.setInterpolate(image.getInterpolate());
                return replacement;
            }
        } finally {
            if (prepared != null && prepared != decoded) {
                prepared.flush();
            }
            decoded.flush();
            delete(imageFile, null);
        }
    }

    private BufferedImage prepareImage(BufferedImage source) {
        int maxDimension = request.mode()
            == CompressPdfPlanFactory.CompressionMode.EXTREME
                ? properties.getExtremeMaxImageDimension()
                : properties.getRecommendedMaxImageDimension();
        double scale = Math.min(
            1.0,
            maxDimension
                / (double) Math.max(source.getWidth(), source.getHeight())
        );
        int width = Math.max(
            1,
            (int) Math.floor(source.getWidth() * scale)
        );
        int height = Math.max(
            1,
            (int) Math.floor(source.getHeight() * scale)
        );
        if (width == source.getWidth()
                && height == source.getHeight()
                && source.getType() == BufferedImage.TYPE_INT_RGB) {
            return source;
        }
        BufferedImage result = new BufferedImage(
            width,
            height,
            BufferedImage.TYPE_INT_RGB
        );
        Graphics2D graphics = result.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, width, height);
            graphics.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BICUBIC
            );
            graphics.setRenderingHint(
                RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY
            );
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return result;
    }

    private void writeJpeg(
            BufferedImage image,
            Path output,
            Path cacheDirectory) throws IOException {
        Iterator<ImageWriter> writers =
            ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            throw new OperationException(
                "COMPRESS_JPEG_WRITER_UNAVAILABLE",
                "No JPEG writer is available for PDF compression"
            );
        }
        ImageWriter writer = writers.next();
        int quality = request.mode()
            == CompressPdfPlanFactory.CompressionMode.EXTREME
                ? properties.getExtremeJpegQuality()
                : properties.getRecommendedJpegQuality();
        try (OutputStream fileOutput = Files.newOutputStream(output);
             BoundedOutputStream bounded = new BoundedOutputStream(
                 fileOutput,
                 properties.getMaxTemporaryImageBytes(),
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
                    "COMPRESS_JPEG_QUALITY_UNSUPPORTED",
                    "The JPEG writer cannot apply compression quality"
                );
            }
            parameters.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            parameters.setCompressionQuality(quality / 100f);
            writer.write(null, new IIOImage(image, null, null), parameters);
            imageOutput.flush();
        } catch (OutputLimitExceededException exception) {
            throw new OperationException(
                "COMPRESS_IMAGE_SIZE_LIMIT_EXCEEDED",
                "A recompressed image exceeds the temporary size limit",
                exception
            );
        } finally {
            writer.dispose();
        }
    }

    private boolean saveCandidate(
            PDDocument document,
            Path candidate) throws IOException {
        try (OutputStream fileOutput = Files.newOutputStream(candidate);
             BoundedOutputStream bounded = new BoundedOutputStream(
                 fileOutput,
                 properties.getMaxOutputBytes(),
                 () -> {
                 }
             )) {
            document.save(
                bounded,
                CompressParameters.DEFAULT_COMPRESSION
            );
            return true;
        } catch (OutputLimitExceededException exception) {
            delete(candidate, null);
            return false;
        }
    }

    private void copySource(Path output) throws IOException {
        try (InputStream input = Files.newInputStream(request.source());
             OutputStream fileOutput = Files.newOutputStream(output);
             BoundedOutputStream bounded = new BoundedOutputStream(
                 fileOutput,
                 properties.getMaxOutputBytes(),
                 () -> {
                 }
             )) {
            input.transferTo(bounded);
        } catch (OutputLimitExceededException exception) {
            throw new OperationException(
                "COMPRESS_OUTPUT_SIZE_LIMIT_EXCEEDED",
                "The compressed PDF exceeds the output limit",
                exception
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
        digest.update(java.nio.charset.StandardCharsets.US_ASCII.encode(
            request.sourceSha256()
        ));
        digest.update(java.nio.charset.StandardCharsets.US_ASCII.encode(
            request.mode().option()
        ));
        byte[] identifier = digest.digest();
        COSArray ids = new COSArray();
        ids.add(new COSString(identifier));
        ids.add(new COSString(identifier));
        document.getDocument().setDocumentID(ids);
    }

    private void writeProgress(Path path, int completed)
            throws IOException {
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(temporary, Integer.toString(completed));
        moveAtomically(temporary, path);
    }

    private void validateStaticRequest() {
        try {
            if (Files.isSymbolicLink(request.source())
                    || !Files.isRegularFile(
                        request.source(),
                        LinkOption.NOFOLLOW_LINKS)
                    || Files.size(request.source()) < 1
                    || Files.size(request.source())
                        > properties.getMaxInputBytes()
                    || Files.exists(
                        request.output(),
                        LinkOption.NOFOLLOW_LINKS)) {
                throw protocolFailure(null);
            }
        } catch (OperationException exception) {
            throw exception;
        } catch (IOException exception) {
            throw protocolFailure(exception);
        }
    }

    private void createDirectories(Path... directories) {
        try {
            for (Path directory : directories) {
                Files.createDirectories(directory);
            }
        } catch (IOException exception) {
            throw new OperationException(
                "COMPRESS_SCRATCH_FAILED",
                "PDF compression scratch storage could not be created",
                exception
            );
        }
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

    private void delete(Path path, RuntimeException failure) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            if (failure != null) {
                failure.addSuppressed(exception);
            }
            logger.error(
                "Could not remove PDF compression scratch {}",
                path,
                exception
            );
        }
    }

    private OperationException imageLimit() {
        return new OperationException(
            "COMPRESS_IMAGE_LIMIT_EXCEEDED",
            "A PDF image exceeds the safe compression limits"
        );
    }

    private OperationException recompressedImageLimit() {
        return new OperationException(
            "COMPRESS_IMAGE_OUTPUT_LIMIT_EXCEEDED",
            "Recompressed PDF images exceed the aggregate byte limit"
        );
    }

    private OperationException encryptedPdf(Throwable cause) {
        return new OperationException(
            "ENCRYPTED_PDF_NOT_SUPPORTED",
            "Compress PDF requires an unencrypted PDF",
            cause
        );
    }

    private OperationException invalidPdf(Throwable cause) {
        return new OperationException(
            "INVALID_PDF",
            "The input is not a readable PDF",
            cause
        );
    }

    private OperationException protocolFailure(Throwable cause) {
        return new OperationException(
            "COMPRESS_WORKER_PROTOCOL_ERROR",
            "The PDF compression worker received invalid state",
            cause
        );
    }
}
