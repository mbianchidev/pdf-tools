package com.pdftools.operations.pdfmarkdown;

import com.pdftools.operations.OperationException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
public class PdfToMarkdownPlanFactory {

    public PdfToMarkdownPlan create(JsonNode options) {
        return new PdfToMarkdownPlan(
            bool(options, "detectHeadings", true),
            bool(options, "detectLists", true),
            bool(options, "detectTables", true),
            bool(options, "includeImages", true),
            bool(options, "preservePageBreaks", true)
        );
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
            throw new OperationException(
                "INVALID_PDF_MARKDOWN_OPTIONS",
                field + " must be a boolean"
            );
        }
        return node.asBoolean();
    }

    public record PdfToMarkdownPlan(
        boolean detectHeadings,
        boolean detectLists,
        boolean detectTables,
        boolean includeImages,
        boolean preservePageBreaks
    ) {
    }
}
