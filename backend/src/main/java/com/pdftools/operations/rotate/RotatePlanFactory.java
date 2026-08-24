package com.pdftools.operations.rotate;

import com.pdftools.operations.OperationException;
import com.pdftools.operations.shared.pages.DuplicatePolicy;
import com.pdftools.operations.shared.pages.PageExpressionParser;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class RotatePlanFactory {

    private static final Set<Integer> ALLOWED_ROTATIONS =
        Set.of(90, 180, 270);
    private static final int MAX_ROTATION_INSTRUCTIONS = 1_000;

    private final PageExpressionParser pageExpressionParser;

    public RotatePlanFactory(PageExpressionParser pageExpressionParser) {
        this.pageExpressionParser = pageExpressionParser;
    }

    public void validateShape(JsonNode options) {
        boolean hasSingleRotation = options.has("rotation");
        boolean hasInstructions = options.has("rotations");
        if (hasInstructions
                && (hasSingleRotation || options.has("pages"))) {
            throw new OperationException(
                "INVALID_ROTATION_OPTIONS",
                "Use either rotation/pages or rotations, not both"
            );
        }
        if (hasInstructions) {
            JsonNode rotations = options.get("rotations");
            if (!rotations.isArray() || rotations.isEmpty()
                    || rotations.size() > MAX_ROTATION_INSTRUCTIONS) {
                throw new OperationException(
                    "INVALID_ROTATIONS",
                    "rotations must be a non-empty array of at most "
                        + MAX_ROTATION_INSTRUCTIONS
                        + " instructions"
                );
            }
            for (JsonNode instruction : rotations) {
                if (!instruction.isObject()) {
                    throw invalidInstruction();
                }
                validateRotation(instruction.get("rotation"));
                JsonNode pages = instruction.get("pages");
                if (pages == null || !pages.isTextual()
                        || pages.asText().isBlank()) {
                    throw invalidInstruction();
                }
            }
            return;
        }
        if (!hasSingleRotation) {
            throw new OperationException(
                "ROTATION_REQUIRED",
                "rotation or rotations is required"
            );
        }
        validateRotation(options.get("rotation"));
        if (options.has("pages")) {
            JsonNode pages = options.get("pages");
            if (!pages.isTextual() || pages.asText().isBlank()) {
                throw new OperationException(
                    "INVALID_ROTATE_PAGES",
                    "pages must be a non-empty page-expression string"
                );
            }
        }
    }

    public RotatePlan create(JsonNode options, int pageCount) {
        validateShape(options);
        Map<Integer, Integer> rotations = new HashMap<>();
        if (options.has("rotations")) {
            for (JsonNode instruction : options.get("rotations")) {
                addInstruction(
                    rotations,
                    instruction.get("pages").asText(),
                    instruction.get("rotation").asInt(),
                    pageCount
                );
            }
        } else {
            addInstruction(
                rotations,
                options.path("pages").asText("all"),
                options.get("rotation").asInt(),
                pageCount
            );
        }
        return new RotatePlan(Map.copyOf(rotations));
    }

    private void addInstruction(
            Map<Integer, Integer> rotations,
            String expression,
            int rotation,
            int pageCount) {
        List<Integer> pages = pageExpressionParser.parse(
            expression,
            pageCount,
            DuplicatePolicy.REJECT
        );
        for (int page : pages) {
            if (rotations.putIfAbsent(page, rotation) != null) {
                throw new OperationException(
                    "OVERLAPPING_ROTATIONS",
                    "Page " + page + " appears in more than one rotation",
                    Map.of("page", page)
                );
            }
        }
    }

    private int validateRotation(JsonNode rotation) {
        if (rotation == null || rotation.isNull()) {
            throw new OperationException(
                "ROTATION_REQUIRED",
                "rotation is required"
            );
        }
        if (!rotation.canConvertToInt()
                || !rotation.isIntegralNumber()
                || !ALLOWED_ROTATIONS.contains(rotation.asInt())) {
            throw new OperationException(
                "INVALID_ROTATION",
                "rotation must be 90, 180, or 270 degrees"
            );
        }
        return rotation.asInt();
    }

    private OperationException invalidInstruction() {
        return new OperationException(
            "INVALID_ROTATION_INSTRUCTION",
            "Every rotation requires non-empty pages and a valid rotation"
        );
    }

    public record RotatePlan(Map<Integer, Integer> rotations) {
        public int rotationFor(int page) {
            return rotations.getOrDefault(page, 0);
        }
    }
}
