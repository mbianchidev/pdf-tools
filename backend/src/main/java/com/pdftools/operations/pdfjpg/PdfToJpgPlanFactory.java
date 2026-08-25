package com.pdftools.operations.pdfjpg;

import com.pdftools.operations.OperationException;
import com.pdftools.operations.shared.pages.DuplicatePolicy;
import com.pdftools.operations.shared.pages.PageExpressionParser;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.List;

@Component
public class PdfToJpgPlanFactory {

    private final PageExpressionParser pageExpressionParser;
    private final PdfToJpgProperties properties;

    public PdfToJpgPlanFactory(
            PageExpressionParser pageExpressionParser,
            PdfToJpgProperties properties) {
        this.pageExpressionParser = pageExpressionParser;
        this.properties = properties;
    }

    public void validateShape(JsonNode options) {
        pages(options);
        dpi(options);
        quality(options);
    }

    public PdfToJpgPlan create(JsonNode options, int pageCount) {
        validateShape(options);
        List<Integer> selected = pageExpressionParser.parse(
            pages(options),
            pageCount,
            DuplicatePolicy.REJECT
        ).stream().sorted().toList();
        if (selected.size() > properties.getMaxSelectedPages()) {
            throw new OperationException(
                "JPG_PAGE_SELECTION_LIMIT_EXCEEDED",
                "Select at most "
                    + properties.getMaxSelectedPages()
                    + " pages"
            );
        }
        return new PdfToJpgPlan(
            selected,
            dpi(options),
            quality(options)
        );
    }

    private String pages(JsonNode options) {
        JsonNode node = options.get("pages");
        if (node == null) {
            return "all";
        }
        if (!node.isTextual() || node.asText().isBlank()) {
            throw new OperationException(
                "INVALID_JPG_PAGES",
                "pages must be a non-empty page-expression string"
            );
        }
        return node.asText();
    }

    private int dpi(JsonNode options) {
        JsonNode node = options.get("dpi");
        int defaultDpi = Math.min(
            Math.max(150, properties.getMinDpi()),
            properties.getMaxDpi()
        );
        int value = node == null
            ? defaultDpi
            : node.asInt(Integer.MIN_VALUE);
        if (node != null && (!node.isIntegralNumber()
                || !node.canConvertToInt())
                || value < properties.getMinDpi()
                || value > properties.getMaxDpi()) {
            throw new OperationException(
                "INVALID_JPG_DPI",
                "dpi must be an integer between "
                    + properties.getMinDpi()
                    + " and "
                    + properties.getMaxDpi()
            );
        }
        return value;
    }

    private int quality(JsonNode options) {
        JsonNode node = options.get("quality");
        int value = node == null ? 85 : node.asInt(Integer.MIN_VALUE);
        if (node != null && (!node.isIntegralNumber()
                || !node.canConvertToInt())
                || value < 10
                || value > 100) {
            throw new OperationException(
                "INVALID_JPG_QUALITY",
                "quality must be an integer between 10 and 100"
            );
        }
        return value;
    }

    public record PdfToJpgPlan(
        List<Integer> pages,
        int dpi,
        int quality
    ) {
        public PdfToJpgPlan {
            pages = List.copyOf(pages);
        }
    }
}
