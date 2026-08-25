package com.pdftools.operations.crop;

import com.pdftools.operations.OperationException;
import com.pdftools.operations.shared.coordinates.NormalizedRectangle;
import com.pdftools.operations.shared.pages.DuplicatePolicy;
import com.pdftools.operations.shared.pages.PageExpressionParser;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class CropPlanFactory {

    private static final int MAX_CROP_INSTRUCTIONS = 1_000;
    private static final double MIN_NORMALIZED_SIZE = 0.001;

    private final PageExpressionParser pageExpressionParser;

    public CropPlanFactory(PageExpressionParser pageExpressionParser) {
        this.pageExpressionParser = pageExpressionParser;
    }

    public void validateShape(JsonNode options) {
        boolean hasShared = options.has("crop");
        boolean hasPerPage = options.has("crops");
        if (hasShared == hasPerPage) {
            throw new OperationException(
                "CROP_REQUIRED",
                "Use either crop or crops"
            );
        }
        if (hasPerPage && options.has("pages")) {
            throw new OperationException(
                "INVALID_CROP_OPTIONS",
                "Top-level pages can only be used with crop"
            );
        }
        if (hasShared) {
            rectangle(options.get("crop"));
            validateOptionalPages(options.get("pages"));
            return;
        }
        JsonNode crops = options.get("crops");
        if (!crops.isArray() || crops.isEmpty()
                || crops.size() > MAX_CROP_INSTRUCTIONS) {
            throw new OperationException(
                "INVALID_CROPS",
                "crops must contain 1-"
                    + MAX_CROP_INSTRUCTIONS
                    + " instructions"
            );
        }
        for (JsonNode crop : crops) {
            if (!crop.isObject()) {
                throw invalidInstruction();
            }
            JsonNode pages = crop.get("pages");
            if (pages == null || !pages.isTextual()
                    || pages.asText().isBlank()
                    || crop.get("rectangle") == null) {
                throw invalidInstruction();
            }
            rectangle(crop.get("rectangle"));
        }
    }

    public CropPlan create(JsonNode options, int pageCount) {
        validateShape(options);
        Map<Integer, NormalizedRectangle> crops = new LinkedHashMap<>();
        if (options.has("crop")) {
            addCrop(
                crops,
                options.path("pages").asText("all"),
                rectangle(options.get("crop")),
                pageCount
            );
        } else {
            for (JsonNode instruction : options.get("crops")) {
                addCrop(
                    crops,
                    instruction.get("pages").asText(),
                    rectangle(instruction.get("rectangle")),
                    pageCount
                );
            }
        }
        return new CropPlan(Map.copyOf(crops));
    }

    private void addCrop(
            Map<Integer, NormalizedRectangle> crops,
            String expression,
            NormalizedRectangle rectangle,
            int pageCount) {
        List<Integer> pages = pageExpressionParser.parse(
            expression,
            pageCount,
            DuplicatePolicy.REJECT
        );
        for (int page : pages) {
            if (crops.putIfAbsent(page, rectangle) != null) {
                throw new OperationException(
                    "OVERLAPPING_CROPS",
                    "Page " + page + " appears in more than one crop",
                    Map.of("page", page)
                );
            }
        }
    }

    private void validateOptionalPages(JsonNode pages) {
        if (pages != null
                && (!pages.isTextual() || pages.asText().isBlank())) {
            throw new OperationException(
                "INVALID_CROP_PAGES",
                "pages must be a non-empty page-expression string"
            );
        }
    }

    private NormalizedRectangle rectangle(JsonNode node) {
        if (node == null || !node.isObject()) {
            throw invalidRectangle();
        }
        for (String field : List.of("x", "y", "width", "height")) {
            if (!node.has(field) || !node.get(field).isNumber()) {
                throw invalidRectangle();
            }
        }
        try {
            NormalizedRectangle rectangle = new NormalizedRectangle(
                node.get("x").asDouble(),
                node.get("y").asDouble(),
                node.get("width").asDouble(),
                node.get("height").asDouble()
            );
            if (rectangle.width() < MIN_NORMALIZED_SIZE
                    || rectangle.height() < MIN_NORMALIZED_SIZE) {
                throw invalidRectangle();
            }
            return rectangle;
        } catch (IllegalArgumentException exception) {
            throw invalidRectangle();
        }
    }

    private OperationException invalidRectangle() {
        return new OperationException(
            "INVALID_CROP_RECTANGLE",
            "Crop rectangles must be finite, contained in the page, "
                + "and at least 0.1% wide and high"
        );
    }

    private OperationException invalidInstruction() {
        return new OperationException(
            "INVALID_CROP_INSTRUCTION",
            "Every crop requires non-empty pages and a rectangle"
        );
    }

    public record CropPlan(Map<Integer, NormalizedRectangle> crops) {
        public NormalizedRectangle cropFor(int page) {
            return crops.get(page);
        }
    }
}
