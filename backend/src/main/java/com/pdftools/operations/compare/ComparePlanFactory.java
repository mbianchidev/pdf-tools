package com.pdftools.operations.compare;

import com.pdftools.operations.OperationException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
public class ComparePlanFactory {

    public ComparePlan create(JsonNode options) {
        int renderDpi = integer(options, "renderDpi", 120);
        int pixelTolerance = integer(
            options,
            "pixelTolerance",
            12
        );
        double layoutTolerancePoints = decimal(
            options,
            "layoutTolerancePoints",
            2.0
        );
        if (renderDpi < 72 || renderDpi > 200) {
            throw invalid(
                "renderDpi must be between 72 and 200"
            );
        }
        if (pixelTolerance < 0 || pixelTolerance > 255) {
            throw invalid(
                "pixelTolerance must be between 0 and 255"
            );
        }
        if (!Double.isFinite(layoutTolerancePoints)
                || layoutTolerancePoints < 0.1
                || layoutTolerancePoints > 20) {
            throw invalid(
                "layoutTolerancePoints must be between 0.1 and 20"
            );
        }
        return new ComparePlan(
            renderDpi,
            pixelTolerance,
            layoutTolerancePoints
        );
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
            throw invalid(field + " must be an integer");
        }
        return node.asInt();
    }

    private double decimal(
            JsonNode options,
            String field,
            double fallback) {
        JsonNode node = options.get(field);
        if (node == null) {
            return fallback;
        }
        if (!node.isNumber()) {
            throw invalid(field + " must be a number");
        }
        return node.asDouble();
    }

    private OperationException invalid(String message) {
        return new OperationException(
            "INVALID_COMPARE_OPTIONS",
            message
        );
    }

    public record ComparePlan(
        int renderDpi,
        int pixelTolerance,
        double layoutTolerancePoints
    ) {
    }
}
