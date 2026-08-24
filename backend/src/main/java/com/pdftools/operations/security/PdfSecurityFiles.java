package com.pdftools.operations.security;

import com.pdftools.operations.BoundedOutputStream;
import com.pdftools.operations.OperationException;
import org.apache.pdfbox.io.RandomAccessStreamCache;
import org.apache.pdfbox.io.ScratchFile;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public final class PdfSecurityFiles {

    private static final Logger logger =
        LoggerFactory.getLogger(PdfSecurityFiles.class);
    private PdfSecurityFiles() {
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

}
