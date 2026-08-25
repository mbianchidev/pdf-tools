package com.pdftools.operations.redact;

import com.pdftools.operations.OperationException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class RedactPlanFactory {

    private static final double MIN_NORMALIZED_SIZE = 0.001;
    private static final double BOUNDS_EPSILON = 0.000001;
    private static final Comparator<RedactArea> AREA_ORDER =
        Comparator.comparingInt(RedactArea::page)
            .thenComparingDouble(RedactArea::x)
            .thenComparingDouble(RedactArea::y)
            .thenComparingDouble(RedactArea::width)
            .thenComparingDouble(RedactArea::height);

    private final RedactProperties properties;

    public RedactPlanFactory(RedactProperties properties) {
        this.properties = properties;
    }

    public void validateShape(JsonNode options) {
        parse(options);
    }

    public RedactPlan parse(JsonNode options) {
        JsonNode areasNode = options.get("areas");
        if (areasNode == null
                || !areasNode.isArray()
                || areasNode.isEmpty()) {
            throw new OperationException(
                "REDACT_AREAS_REQUIRED",
                "Add at least one redaction area"
            );
        }
        if (areasNode.size() > properties.getMaxAreas()) {
            throw new OperationException(
                "REDACT_AREA_LIMIT_EXCEEDED",
                "Use at most "
                    + properties.getMaxAreas()
                    + " redaction areas"
            );
        }

        List<RedactArea> areas = new ArrayList<>(areasNode.size());
        Set<RedactArea> unique = new HashSet<>();
        Map<Integer, Integer> pageCounts = new HashMap<>();
        for (JsonNode areaNode : areasNode) {
            RedactArea area = area(areaNode);
            if (!unique.add(area)) {
                throw new OperationException(
                    "DUPLICATE_REDACTION_AREA",
                    "Redaction areas must not be duplicated"
                );
            }
            int count = pageCounts.merge(area.page(), 1, Integer::sum);
            if (count > properties.getMaxAreasPerPage()) {
                throw new OperationException(
                    "REDACT_PAGE_AREA_LIMIT_EXCEEDED",
                    "A page contains too many redaction areas"
                );
            }
            areas.add(area);
        }
        areas.sort(AREA_ORDER);
        return new RedactPlan(areas);
    }

    public RedactPlan create(JsonNode options, int pageCount) {
        RedactPlan plan = parse(options);
        for (RedactArea area : plan.areas()) {
            if (area.page() > pageCount) {
                throw new OperationException(
                    "REDACT_PAGE_OUT_OF_RANGE",
                    "Redaction page " + area.page()
                        + " exceeds the document page count"
                );
            }
        }
        return plan;
    }

    private RedactArea area(JsonNode node) {
        if (!node.isObject()) {
            throw invalidArea();
        }
        int page = integer(node, "page");
        double x = number(node, "x");
        double y = number(node, "y");
        double width = number(node, "width");
        double height = number(node, "height");
        if (page < 1
                || x < 0
                || y < 0
                || width < MIN_NORMALIZED_SIZE
                || height < MIN_NORMALIZED_SIZE
                || x + width > 1 + BOUNDS_EPSILON
                || y + height > 1 + BOUNDS_EPSILON) {
            throw invalidArea();
        }
        return new RedactArea(
            page,
            (float) x,
            (float) y,
            (float) width,
            (float) height
        );
    }

    private int integer(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null
                || !value.isIntegralNumber()
                || !value.canConvertToInt()) {
            throw invalidArea();
        }
        return value.intValue();
    }

    private double number(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isNumber()) {
            throw invalidArea();
        }
        double parsed = value.doubleValue();
        if (!Double.isFinite(parsed)) {
            throw invalidArea();
        }
        return parsed;
    }

    private OperationException invalidArea() {
        return new OperationException(
            "INVALID_REDACTION_AREA",
            "Each redaction area requires a page and normalized "
                + "x, y, width, and height inside the page"
        );
    }

    public record RedactPlan(List<RedactArea> areas) {
        public RedactPlan {
            areas = List.copyOf(areas);
        }

        public List<RedactArea> forPage(int page) {
            return areas.stream()
                .filter(area -> area.page() == page)
                .toList();
        }
    }

    public record RedactArea(
        int page,
        float x,
        float y,
        float width,
        float height
    ) {
    }
}
