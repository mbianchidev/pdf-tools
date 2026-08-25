package com.pdftools.operations;

import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class PdfOperationValidation {

    private static final Set<String> PDF_MEDIA_TYPES = Set.of(
        "application/pdf",
        "application/x-pdf",
        "application/octet-stream"
    );

    private PdfOperationValidation() {
    }

    public static void requireSinglePdf(
            OperationSubmission submission,
            String operationName) {
        if (submission.files().size() != 1) {
            throw new OperationException(
                "INVALID_FILE_COUNT",
                operationName + " requires exactly one PDF file"
            );
        }
        OperationSubmission.UploadDescriptor file =
            submission.files().getFirst();
        if (!hasPdfStem(file.filename())
                || !PDF_MEDIA_TYPES.contains(
                    file.mediaType().toLowerCase(Locale.ROOT))) {
            throw new OperationException(
                "INVALID_FILE_TYPE",
                operationName + " input must be a PDF"
            );
        }
    }

    public static void validateOptionalOutputFilename(
            JsonNode options,
            String extension,
            int maxBytes,
            String operationName) {
        if (!options.has("outputFilename")) {
            return;
        }
        if (!options.get("outputFilename").isTextual()) {
            throw new OperationException(
                "INVALID_OPTIONS",
                "outputFilename must be a string"
            );
        }
        String filename = options.get("outputFilename").asText().trim();
        if (!filename.isEmpty()) {
            validateOutputFilename(
                filename,
                extension,
                maxBytes,
                operationName
            );
        }
    }

    public static void validateOutputFilename(
            String filename,
            String extension,
            int maxBytes,
            String operationName) {
        if (!filename.toLowerCase(Locale.ROOT).endsWith(extension)
                || filename.getBytes(StandardCharsets.UTF_8).length
                    > maxBytes) {
            throw new OperationException(
                "INVALID_OUTPUT_FILENAME",
                operationName + " outputFilename must end with "
                    + extension
                    + " and stay within "
                    + maxBytes
                    + " bytes",
                Map.of("maxBytes", maxBytes)
            );
        }
    }

    private static boolean hasPdfStem(String filename) {
        String lower = filename.toLowerCase(Locale.ROOT);
        return lower.endsWith(".pdf")
            && !filename.substring(0, filename.length() - 4).isBlank();
    }
}
