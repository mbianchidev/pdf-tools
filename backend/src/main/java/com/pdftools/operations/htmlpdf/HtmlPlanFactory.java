package com.pdftools.operations.htmlpdf;

import com.pdftools.operations.OperationException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class HtmlPlanFactory {

    private static final Map<String, String> PAGE_SIZES = Map.of(
        "a4", "A4",
        "letter", "Letter",
        "legal", "Legal"
    );
    private static final Set<String> ORIENTATIONS = Set.of(
        "portrait",
        "landscape"
    );

    public HtmlPlan create(JsonNode options) {
        String requestedPageSize = text(options, "pageSize", "a4");
        String pageSize = PAGE_SIZES.get(requestedPageSize);
        if (pageSize == null) {
            throw new OperationException(
                "INVALID_HTML_PAGE_SIZE",
                "pageSize must be a4, letter, or legal"
            );
        }
        String orientation = text(
            options,
            "orientation",
            "portrait"
        );
        if (!ORIENTATIONS.contains(orientation)) {
            throw new OperationException(
                "INVALID_HTML_ORIENTATION",
                "orientation must be portrait or landscape"
            );
        }
        boolean printBackground = bool(
            options,
            "printBackground",
            true
        );
        int marginMm = integer(options, "marginMm", 10);
        if (marginMm < 0 || marginMm > 50) {
            throw new OperationException(
                "INVALID_HTML_MARGIN",
                "marginMm must be between 0 and 50"
            );
        }
        return new HtmlPlan(
            pageSize,
            orientation.equals("landscape"),
            printBackground,
            marginMm
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

    private int integer(
            JsonNode options,
            String field,
            int fallback) {
        JsonNode node = options.get(field);
        if (node == null) {
            return fallback;
        }
        if (!node.isInt()) {
            throw invalidOptions(field + " must be an integer");
        }
        return node.asInt();
    }

    private OperationException invalidOptions(String message) {
        return new OperationException("INVALID_HTML_OPTIONS", message);
    }

    public record HtmlPlan(
        String pageSize,
        boolean landscape,
        boolean printBackground,
        int marginMm
    ) {
    }
}
