package com.pdftools.operations.security;

import com.pdftools.operations.BoundedOutputStream;
import com.pdftools.operations.OperationException;
import org.apache.pdfbox.io.RandomAccessStreamCache;
import org.apache.pdfbox.io.ScratchFile;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class PdfSecurityFiles {

    private static final Logger logger =
        LoggerFactory.getLogger(PdfSecurityFiles.class);
    private static final int HEADER_SCAN_BYTES = 1024;

    private PdfSecurityFiles() {
    }

    public static void requirePdfHeader(Path source) {
        try (InputStream input = Files.newInputStream(source)) {
            String prefix = new String(
                input.readNBytes(HEADER_SCAN_BYTES),
                StandardCharsets.ISO_8859_1
            );
            if (!prefix.contains("%PDF-")) {
                throw invalidPdf();
            }
        } catch (OperationException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new OperationException(
                "INVALID_PDF",
                "The input is not a readable PDF",
                exception
            );
        }
    }

    public static RandomAccessStreamCache.StreamCacheCreateFunction
            scratchCache(
                Path workspace,
                String errorCode,
                String errorMessage) {
        Path scratchDirectory = workspace.resolve(".pdfbox-scratch");
        try {
            Files.createDirectories(scratchDirectory);
        } catch (IOException exception) {
            throw new OperationException(
                errorCode,
                errorMessage,
                exception
            );
        }
        return () -> new ScratchFile(scratchDirectory.toFile());
    }

    public static void save(
            PDDocument document,
            Path output,
            long maxOutputBytes,
            Runnable cancellationCheck) throws IOException {
        try (OutputStream fileOutput = Files.newOutputStream(output);
             BoundedOutputStream bounded = new BoundedOutputStream(
                 fileOutput,
                 maxOutputBytes,
                 cancellationCheck
             )) {
            document.save(bounded);
        }
    }

    public static <T extends RuntimeException> T cleanup(
            Path output,
            T failure,
            String cleanupCode,
            String cleanupMessage) {
        try {
            Files.deleteIfExists(output);
        } catch (IOException exception) {
            OperationException cleanupFailure = new OperationException(
                cleanupCode,
                cleanupMessage,
                exception
            );
            failure.addSuppressed(cleanupFailure);
            logger.error(
                "Could not remove partial PDF security output {}",
                output,
                cleanupFailure
            );
        }
        return failure;
    }

    private static OperationException invalidPdf() {
        return new OperationException(
            "INVALID_PDF",
            "The input is not a readable PDF"
        );
    }
}
