package com.pdftools.operations.split;

import com.pdftools.operations.BoundedOutputStream;
import com.pdftools.operations.OperationCancelledException;
import com.pdftools.operations.OperationException;
import com.pdftools.operations.OutputLimitExceededException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.contentstream.PDContentStream;
import org.apache.pdfbox.io.RandomAccessReadBufferedFile;
import org.apache.pdfbox.io.RandomAccessStreamCache;
import org.apache.pdfbox.io.ScratchFile;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntConsumer;

@Component
public class PdfSplitEngine {

    private static final Logger logger =
        LoggerFactory.getLogger(PdfSplitEngine.class);
    private static final int HEADER_SCAN_BYTES = 1024;
    private static final int CANCELLATION_PAGE_INTERVAL = 25;

    private final SplitPlanFactory planFactory;
    private final SplitProperties properties;
    private final PdfPageTreeReader pageTreeReader;
    private final PdfResourcePruner resourcePruner;
    private final PdfContentDecoder contentDecoder;

    public PdfSplitEngine(
            SplitPlanFactory planFactory,
            SplitProperties properties) {
        this.planFactory = planFactory;
        this.properties = properties;
        this.pageTreeReader = new PdfPageTreeReader(properties);
        this.resourcePruner = new PdfResourcePruner(properties);
        this.contentDecoder = new PdfContentDecoder();
    }

    public SplitResult split(
            Path sourcePath,
            JsonNode options,
            Path workspace,
            IntConsumer progress,
            Runnable cancellationCheck) {
        return process(
            sourcePath,
            workspace,
            progress,
            cancellationCheck,
            pageCount -> planFactory.create(options, pageCount),
            (page, sourcePageNumber) -> {
            }
        );
    }

    public Path copySelectedPages(
            Path sourcePath,
            Path workspace,
            PageSelector selector,
            IntConsumer progress,
            Runnable cancellationCheck) {
        return copySelectedPages(
            sourcePath,
            workspace,
            selector,
            (page, sourcePageNumber) -> {
            },
            progress,
            cancellationCheck
        );
    }

    public Path copySelectedPages(
            Path sourcePath,
            Path workspace,
            PageSelector selector,
            PageTransformer transformer,
            IntConsumer progress,
            Runnable cancellationCheck) {
        SplitResult result = process(
            sourcePath,
            workspace,
            progress,
            cancellationCheck,
            pageCount -> List.of(new SplitGroup(
                1,
                List.copyOf(selector.select(pageCount))
            )),
            transformer
        );
        return result.parts().getFirst().path();
    }

    private SplitResult process(
            Path sourcePath,
            Path workspace,
            IntConsumer progress,
            Runnable cancellationCheck,
            java.util.function.IntFunction<List<SplitGroup>> planner,
            PageTransformer transformer) {
        requirePdfHeader(sourcePath);
        Path scratchDirectory = workspace.resolve(".pdfbox-scratch");
        try {
            Files.createDirectories(scratchDirectory);
        } catch (IOException exception) {
            throw new OperationException(
                "SPLIT_SCRATCH_FAILED",
                "The split scratch directory could not be created",
                exception
            );
        }
        RandomAccessStreamCache.StreamCacheCreateFunction scratchCache =
            () -> new ScratchFile(scratchDirectory.toFile());
        List<Path> createdParts = new ArrayList<>();
        byte[] sourceHash = sha256(sourcePath);

        try (RandomAccessReadBufferedFile randomAccess =
                 new RandomAccessReadBufferedFile(sourcePath);
             PDDocument source = Loader.loadPDF(randomAccess, scratchCache)) {
            validateDocument(source);
            PdfPageTreeReader.Result pageTree = pageTreeReader.read(
                source,
                cancellationCheck
            );
            List<SplitGroup> groups = planner.apply(pageTree.pages().size());
            int selectedPages = groups.stream()
                .mapToInt(group -> group.pages().size())
                .sum();
            int processedPages = 0;
            long totalOutputBytes = 0;
            SplitDecodedBudget decodedBudget = new SplitDecodedBudget(
                properties.getMaxTotalDecodedBytes()
            );
            SplitDecodedBudget resourceScratchBudget =
                new SplitDecodedBudget(
                    properties.getMaxResourceScratchBytes(),
                    "SPLIT_RESOURCE_SCRATCH_LIMIT_EXCEEDED",
                    "Split resource scratch data exceeds the configured limit"
                );
            SplitStructureBudget resourceStructureBudget =
                new SplitStructureBudget(
                    properties.getMaxTotalResourceNodes()
                );
            int lastProgress = 5;
            progress.accept(lastProgress);
            cancellationCheck.run();

            List<SplitResult.Part> parts = new ArrayList<>(groups.size());
            for (SplitGroup group : groups) {
                Path partPath = workspace.resolve(String.format(
                    Locale.ROOT,
                    "part-%04d.pdf",
                    group.position()
                ));
                createdParts.add(partPath);
                try (PDDocument destination = new PDDocument(scratchCache)) {
                    destination.setVersion(source.getVersion());
                    PdfResourcePruner.Session resources =
                        resourcePruner.openSession(
                            destination,
                            cancellationCheck,
                            pageTree.pageLocalReferences(),
                            resourceScratchBudget,
                            resourceStructureBudget,
                            nestedContent -> materializeDecodedContent(
                                destination,
                                nestedContent,
                                decodedBudget,
                                cancellationCheck
                            ).stream()
                        );
                    for (int pageNumber : group.pages()) {
                        if (processedPages
                                % CANCELLATION_PAGE_INTERVAL == 0) {
                            cancellationCheck.run();
                        }
                        PDPage sourcePage = pageTree.pages().get(
                            pageNumber - 1
                        );
                        PDStream content = materializeDecodedContent(
                            destination,
                            sourcePage,
                            decodedBudget,
                            cancellationCheck
                        ).stream();
                        PDPage copiedPage = pageLocalCopy(
                            sourcePage,
                            content,
                            resources
                        );
                        transformer.transform(copiedPage, pageNumber);
                        destination.addPage(copiedPage);
                        processedPages++;
                    }
                    setDeterministicId(destination, sourceHash, group);
                    totalOutputBytes = saveBoundedPart(
                        destination,
                        partPath,
                        totalOutputBytes,
                        cancellationCheck
                    );
                }
                parts.add(new SplitResult.Part(
                    group.position(),
                    group.pages(),
                    partPath
                ));
                int nextProgress = 5 + (int) Math.floor(
                    processedPages * 85.0 / selectedPages
                );
                if (nextProgress >= lastProgress + 2) {
                    lastProgress = Math.min(nextProgress, 90);
                    progress.accept(lastProgress);
                }
            }

            cancellationCheck.run();
            return new SplitResult(parts);
        } catch (InvalidPasswordException exception) {
            deleteParts(createdParts);
            throw encryptedPdf();
        } catch (OutputLimitExceededException exception) {
            deleteParts(createdParts);
            throw new OperationException(
                "SPLIT_OUTPUT_SIZE_LIMIT_EXCEEDED",
                "Split outputs exceed the configured size limit",
                Map.of(
                    "maxOutputBytes", properties.getMaxOutputBytes(),
                    "maxTotalOutputBytes",
                    properties.getMaxTotalOutputBytes()
                )
            );
        } catch (ArithmeticException exception) {
            deleteParts(createdParts);
            throw new OperationException(
                "SPLIT_DECODED_CONTENT_LIMIT_EXCEEDED",
                "Split decoded content exceeds the configured total limit"
            );
        } catch (OperationException | OperationCancelledException exception) {
            deleteParts(createdParts);
            throw exception;
        } catch (IOException exception) {
            deleteParts(createdParts);
            logger.warn("Failed to split PDF {}", sourcePath, exception);
            throw new OperationException(
                "INVALID_PDF",
                "The input is not a readable PDF",
                exception
            );
        }
    }

    @FunctionalInterface
    public interface PageSelector {
        List<Integer> select(int pageCount);
    }

    @FunctionalInterface
    public interface PageTransformer {
        void transform(PDPage page, int sourcePageNumber);
    }

    private void validateDocument(PDDocument source) {
        if (source.isEncrypted()) {
            throw encryptedPdf();
        }
        if (source.getDocumentCatalog().getOCProperties() != null) {
            throw new OperationException(
                "OPTIONAL_CONTENT_UNSUPPORTED",
                "Split does not support PDFs with optional-content layers"
            );
        }
    }

    private long saveBoundedPart(
            PDDocument destination,
            Path partPath,
            long totalOutputBytes,
            Runnable cancellationCheck) throws IOException {
        long remainingBytes = properties.getMaxTotalOutputBytes()
            - totalOutputBytes;
        long outputLimit = Math.min(
            properties.getMaxOutputBytes(),
            remainingBytes
        );
        if (outputLimit < 1) {
            throw new OutputLimitExceededException(
                properties.getMaxTotalOutputBytes()
            );
        }
        try (OutputStream fileOutput = Files.newOutputStream(partPath);
             BoundedOutputStream boundedOutput = new BoundedOutputStream(
                 fileOutput,
                 outputLimit,
                 cancellationCheck
             )) {
            destination.save(boundedOutput);
            return Math.addExact(
                totalOutputBytes,
                boundedOutput.getCount()
            );
        }
    }

    private PDPage pageLocalCopy(
            PDPage source,
            PDStream contents,
            PdfResourcePruner.Session resources) throws IOException {
        COSDictionary dictionary = new COSDictionary();
        dictionary.setItem(COSName.TYPE, COSName.PAGE);
        dictionary.setItem(COSName.CONTENTS, contents);
        PDPage copy = new PDPage(dictionary);
        copy.setMediaBox(PdfCosUtils.copyRectangle(source.getMediaBox()));
        copy.setCropBox(PdfCosUtils.copyRectangle(source.getCropBox()));
        copy.setBleedBox(PdfCosUtils.copyRectangle(source.getBleedBox()));
        copy.setTrimBox(PdfCosUtils.copyRectangle(source.getTrimBox()));
        copy.setArtBox(PdfCosUtils.copyRectangle(source.getArtBox()));
        copy.setRotation(source.getRotation());
        copy.setUserUnit(source.getUserUnit());
        copy.setAnnotations(List.of());

        PDResources sourceResources = source.getResources() == null
            ? new PDResources()
            : source.getResources();
        if (source.getCOSObject().containsKey(COSName.GROUP)) {
            COSDictionary sourceGroup = source.getCOSObject()
                .getCOSDictionary(COSName.GROUP);
            if (sourceGroup == null) {
                throw new OperationException(
                    "UNSUPPORTED_TRANSPARENCY_GROUP",
                    "PDF transparency group metadata is not supported"
                );
            }
            dictionary.setItem(
                COSName.GROUP,
                resources.sanitizeTransparencyGroup(sourceGroup)
            );
        }
        copy.setResources(resources.prune(
            copy,
            sourceResources,
            source.getCOSObject()
        ));
        return copy;
    }

    private MaterializedContent materializeDecodedContent(
            PDDocument destination,
            PDContentStream content,
            SplitDecodedBudget budget,
            Runnable cancellationCheck) throws IOException {
        long remainingTotalBytes = budget.remainingBytes();
        if (remainingTotalBytes < 1) {
            throw new OperationException(
                "SPLIT_DECODED_CONTENT_LIMIT_EXCEEDED",
                "Split decoded content exceeds the configured total limit"
            );
        }
        long limit = Math.min(
            properties.getMaxDecodedPageBytes(),
            remainingTotalBytes
        );
        try {
            PdfContentDecoder.Result decoded = contentDecoder.materialize(
                destination,
                content,
                limit,
                budget,
                cancellationCheck
            );
            return new MaterializedContent(
                decoded.stream(),
                decoded.sizeBytes()
            );
        } catch (OutputLimitExceededException exception) {
            String code =
                remainingTotalBytes < properties.getMaxDecodedPageBytes()
                    ? "SPLIT_DECODED_CONTENT_LIMIT_EXCEEDED"
                    : "PDF_PAGE_CONTENT_LIMIT_EXCEEDED";
            throw new OperationException(
                code,
                "Decoded PDF content exceeds the configured limit",
                Map.of(
                    "maxDecodedPageBytes",
                    properties.getMaxDecodedPageBytes(),
                    "maxTotalDecodedBytes",
                    properties.getMaxTotalDecodedBytes()
                )
            );
        }
    }

    private byte[] sha256(Path sourcePath) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(sourcePath)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    if (read > 0) {
                        digest.update(buffer, 0, read);
                    }
                }
            }
            return digest.digest();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                "SHA-256 is unavailable",
                exception
            );
        } catch (IOException exception) {
            throw new OperationException(
                "INVALID_PDF",
                "The input is not a readable PDF",
                exception
            );
        }
    }

    private void setDeterministicId(
            PDDocument destination,
            byte[] sourceHash,
            SplitGroup group) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(sourceHash);
            digest.update(ByteBuffer.allocate(Integer.BYTES)
                .putInt(group.position())
                .array());
            for (int page : group.pages()) {
                digest.update(ByteBuffer.allocate(Integer.BYTES)
                    .putInt(page)
                    .array());
            }
            COSString id = new COSString(digest.digest());
            COSArray ids = new COSArray();
            ids.add(id);
            ids.add(new COSString(id.getBytes()));
            destination.getDocument().setDocumentID(ids);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                "SHA-256 is unavailable",
                exception
            );
        }
    }

    private void requirePdfHeader(Path sourcePath) {
        try (InputStream input = Files.newInputStream(sourcePath)) {
            String prefix = new String(
                input.readNBytes(HEADER_SCAN_BYTES),
                StandardCharsets.ISO_8859_1
            );
            if (!prefix.contains("%PDF-")) {
                throw new OperationException(
                    "INVALID_PDF",
                    "The input is not a readable PDF"
                );
            }
        } catch (OperationException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new OperationException(
                "INVALID_PDF",
                "The input is not a readable PDF"
            );
        }
    }

    private OperationException encryptedPdf() {
        return new OperationException(
            "ENCRYPTED_PDF",
            "Unlock the PDF before splitting it"
        );
    }

    private void deleteParts(List<Path> parts) {
        parts.stream()
            .sorted(Comparator.reverseOrder())
            .forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException exception) {
                    logger.warn(
                        "Failed to remove partial split output {}",
                        path,
                        exception
                    );
                }
            });
    }

    private record MaterializedContent(PDStream stream, long sizeBytes) {
    }

}
