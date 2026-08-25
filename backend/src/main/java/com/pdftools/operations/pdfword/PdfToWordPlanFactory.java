package com.pdftools.operations.pdfword;

import com.pdftools.operations.OperationException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.Locale;
import java.util.Set;

@Component
public class PdfToWordPlanFactory {

    private static final Set<String> MODES = Set.of(
        "editable",
        "visual"
    );

    public PdfToWordPlan create(JsonNode options) {
        String mode = text(options, "mode", "editable");
        if (!MODES.contains(mode)) {
            throw new OperationException(
                "INVALID_PDF_WORD_MODE",
                "mode must be editable or visual"
            );
        }
        return new PdfToWordPlan(
            mode,
            bool(options, "includeImages", true),
            bool(options, "detectTables", true),
            bool(options, "preservePageBreaks", true)
        );
    }

    private String text(
            JsonNode options,
            String field,
            String fallback) {
        JsonNode node = options.get(field);
        if (node == null) {
            return fallback;
        }
        if (!node.isTextual()) {
            throw invalidOptions(field + " must be a string");
        }
        return node.asText().trim().toLowerCase(Locale.ROOT);
    }

    private boolean bool(
            JsonNode options,
            String field,
            boolean fallback) {
        JsonNode node = options.get(field);
        if (node == null) {
            return fallback;
        }
        if (!node.isBoolean()) {
            throw invalidOptions(field + " must be a boolean");
        }
        return node.asBoolean();
    }

    private OperationException invalidOptions(String message) {
        return new OperationException(
            "INVALID_PDF_WORD_OPTIONS",
            message
        );
    }

    public record PdfToWordPlan(
        String mode,
        boolean includeImages,
        boolean detectTables,
        boolean preservePageBreaks
    ) {
    }
}
