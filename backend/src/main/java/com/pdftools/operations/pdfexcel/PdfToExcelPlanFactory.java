package com.pdftools.operations.pdfexcel;

import com.pdftools.operations.OperationException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.Locale;
import java.util.Set;

@Component
public class PdfToExcelPlanFactory {

    private static final Set<String> SHEET_MODES = Set.of(
        "pages",
        "tables"
    );

    public PdfToExcelPlan create(JsonNode options) {
        String sheetMode = text(options, "sheetMode", "pages");
        if (!SHEET_MODES.contains(sheetMode)) {
            throw new OperationException(
                "INVALID_PDF_EXCEL_SHEET_MODE",
                "sheetMode must be pages or tables"
            );
        }
        return new PdfToExcelPlan(
            sheetMode,
            bool(options, "includeNonTableText", true)
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
            "INVALID_PDF_EXCEL_OPTIONS",
            message
        );
    }

    public record PdfToExcelPlan(
        String sheetMode,
        boolean includeNonTableText
    ) {
    }
}
