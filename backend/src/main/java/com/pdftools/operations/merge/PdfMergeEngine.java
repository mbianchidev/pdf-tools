package com.pdftools.operations.merge;

import com.pdftools.operations.OperationException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBufferedFile;
import org.apache.pdfbox.io.RandomAccessStreamCache;
import org.apache.pdfbox.io.ScratchFile;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;

@Component
public class PdfMergeEngine {

    private static final Logger logger = LoggerFactory.getLogger(PdfMergeEngine.class);
    private static final int HEADER_SCAN_BYTES = 1024;

    private final MergeProperties properties;

    public PdfMergeEngine(MergeProperties properties) {
        this.properties = properties;
    }

    public void merge(
            List<MergeSource> sources,
            Path destination,
            IntConsumer progress,
            Runnable cancellationCheck) {
        List<MergeSource> orderedSources = sources.stream()
            .sorted(Comparator.comparingInt(MergeSource::position))
            .toList();
        validateOrder(orderedSources);
        Path scratchDirectory = orderedSources.getFirst().path()
            .getParent()
            .resolve(".pdfbox-scratch");

        try {
            Files.createDirectories(scratchDirectory);
        } catch (IOException exception) {
            throw new OperationException(
                "MERGE_SCRATCH_FAILED",
                "The merge scratch directory could not be created",
                exception
            );
        }
        RandomAccessStreamCache.StreamCacheCreateFunction scratchCache =
            () -> new ScratchFile(scratchDirectory.toFile());

        try (PDDocument merged = new PDDocument(scratchCache)) {
            PDFMergerUtility merger = new PDFMergerUtility();
            int totalPages = 0;
            for (int sourceIndex = 0; sourceIndex < orderedSources.size(); sourceIndex++) {
                cancellationCheck.run();
                MergeSource source = orderedSources.get(sourceIndex);
                requirePdfHeader(source);

                try (RandomAccessReadBufferedFile randomAccess =
                         new RandomAccessReadBufferedFile(source.path());
                     PDDocument document = Loader.loadPDF(
                         randomAccess,
                         scratchCache
                     )) {
                    if (document.isEncrypted()) {
                        throw encryptedPdf(source);
                    }
                    int pageCount = document.getNumberOfPages();
                    validatePageCount(source, pageCount, totalPages);
                    cancellationCheck.run();
                    merger.appendDocument(merged, document);
                    cancellationCheck.run();
                    totalPages += pageCount;
                } catch (InvalidPasswordException exception) {
                    throw encryptedPdf(source);
                } catch (IOException exception) {
                    logger.warn(
                        "Merge input {} ({}) is not readable",
                        source.position(),
                        source.filename(),
                        exception
                    );
                    throw invalidPdf(source);
                }

                int sourceProgress = 10 + (int) Math.floor(
                    ((sourceIndex + 1) * 75.0) / orderedSources.size()
                );
                progress.accept(Math.min(sourceProgress, 85));
            }

            cancellationCheck.run();
            progress.accept(90);
            merged.save(destination.toFile());
            progress.accept(95);
        } catch (OperationException exception) {
            throw exception;
        } catch (IOException exception) {
            logger.error("Failed to write merged PDF {}", destination, exception);
            throw new OperationException(
                "MERGE_WRITE_FAILED",
                "The merged PDF could not be written",
                exception
            );
        }
    }

    private void validateOrder(List<MergeSource> sources) {
        if (sources.size() < 2 || sources.size() > properties.getMaxFiles()) {
            throw new OperationException(
                "INVALID_FILE_COUNT",
                "Merge requires between 2 and " + properties.getMaxFiles() + " PDF files"
            );
        }

        long totalBytes = 0;
        for (int index = 0; index < sources.size(); index++) {
            MergeSource source = sources.get(index);
            if (source.position() != index + 1) {
                throw new OperationException(
                    "INVALID_INPUT_ORDER",
                    "Merge input positions must be unique and contiguous"
                );
            }
            try {
                totalBytes = Math.addExact(totalBytes, source.sizeBytes());
            } catch (ArithmeticException exception) {
                throw new OperationException(
                    "MERGE_INPUT_TOO_LARGE",
                    "Merge input size exceeds the configured limit"
                );
            }
        }
        if (totalBytes > properties.getMaxTotalInputBytes()) {
            throw new OperationException(
                "MERGE_INPUT_TOO_LARGE",
                "Merge inputs exceed the "
                    + properties.getMaxTotalInputBytes() + "-byte total limit",
                Map.of("maxTotalInputBytes", properties.getMaxTotalInputBytes())
            );
        }
    }

    private void requirePdfHeader(MergeSource source) {
        try (InputStream input = Files.newInputStream(source.path())) {
            byte[] prefix = input.readNBytes(HEADER_SCAN_BYTES);
            String header = new String(prefix, StandardCharsets.ISO_8859_1);
            if (!header.contains("%PDF-")) {
                throw invalidPdf(source);
            }
        } catch (OperationException exception) {
            throw exception;
        } catch (IOException exception) {
            logger.warn("Failed to read merge input {}", source.path(), exception);
            throw invalidPdf(source);
        }
    }

    private void validatePageCount(MergeSource source, int pageCount, int currentTotalPages) {
        if (pageCount < 1) {
            throw new OperationException(
                "EMPTY_PDF",
                "Input " + source.position() + " does not contain any pages",
                Map.of("position", source.position(), "filename", source.filename())
            );
        }
        if (pageCount > properties.getMaxPagesPerFile()) {
            throw new OperationException(
                "PDF_PAGE_LIMIT_EXCEEDED",
                "Input " + source.position() + " exceeds the per-file page limit",
                Map.of(
                    "position", source.position(),
                    "maxPagesPerFile", properties.getMaxPagesPerFile()
                )
            );
        }
        if ((long) currentTotalPages + pageCount > properties.getMaxTotalPages()) {
            throw new OperationException(
                "MERGE_PAGE_LIMIT_EXCEEDED",
                "Merged output would exceed the total page limit",
                Map.of("maxTotalPages", properties.getMaxTotalPages())
            );
        }
    }

    private OperationException invalidPdf(MergeSource source) {
        return new OperationException(
            "INVALID_PDF",
            "Input " + source.position() + " is not a readable PDF",
            Map.of("position", source.position(), "filename", source.filename())
        );
    }

    private OperationException encryptedPdf(MergeSource source) {
        return new OperationException(
            "ENCRYPTED_PDF",
            "Input " + source.position()
                + " is encrypted. Unlock it before merging.",
            Map.of(
                "position", source.position(),
                "filename", source.filename()
            )
        );
    }
}
