package com.pdftools.operations.shared.pdf;

import com.pdftools.operations.OperationException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class PdfInputValidator {

    private static final int HEADER_SCAN_BYTES = 1024;

    private PdfInputValidator() {
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

    private static OperationException invalidPdf() {
        return new OperationException(
            "INVALID_PDF",
            "The input is not a readable PDF"
        );
    }
}
