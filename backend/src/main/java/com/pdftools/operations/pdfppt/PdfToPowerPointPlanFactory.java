package com.pdftools.operations.pdfppt;

import com.pdftools.operations.OperationException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.Locale;
import java.util.Set;

@Component
public class PdfToPowerPointPlanFactory {

    private static final Set<String> MODES = Set.of(
        "editable",
        "visual"
    );
    private static final Set<String> SLIDE_SIZES = Set.of(
        "source",
        "widescreen",
        "standard"
    );

    public PdfToPowerPointPlan create(JsonNode options) {
        String mode = text(options, "mode", "editable");
        if (!MODES.contains(mode)) {
            throw new OperationException(
                "INVALID_PDF_POWERPOINT_MODE",
                "mode must be editable or visual"
            );
        }
        String slideSize = text(options, "slideSize", "source");
        if (!SLIDE_SIZES.contains(slideSize)) {
            throw new OperationException(
                "INVALID_PDF_POWERPOINT_SLIDE_SIZE",
                "slideSize must be source, widescreen, or standard"
            );
        }
        return new PdfToPowerPointPlan(
            mode,
            slideSize,
            bool(options, "includeImages", true),
            bool(options, "detectTables", true)
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
            "INVALID_PDF_POWERPOINT_OPTIONS",
            message
        );
    }

    public record PdfToPowerPointPlan(
        String mode,
        String slideSize,
        boolean includeImages,
        boolean detectTables
    ) {
    }
}
