package com.pdftools.operations.jpgpdf;

import com.pdftools.operations.OperationException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.Locale;
import java.util.Set;

@Component
public class JpgToPdfPlanFactory {

    private static final Set<String> PAGE_SIZES = Set.of(
        "fit",
        "a4",
        "letter",
        "legal"
    );
    private static final Set<String> ORIENTATIONS = Set.of(
        "auto",
        "portrait",
        "landscape"
    );
    private final JpgToPdfProperties properties;

    public JpgToPdfPlanFactory(JpgToPdfProperties properties) {
        if (properties.getFitImageDpi() < 1
                || properties.getMaxFitPagePoints() <= 288) {
            throw new IllegalStateException(
                "JPG-to-PDF fit page configuration is invalid"
            );
        }
        this.properties = properties;
    }

    public void validateShape(JsonNode options) {
        pageSize(options);
        orientation(options);
        margin(options);
    }

    public JpgToPdfPlan create(JsonNode options) {
        validateShape(options);
        return new JpgToPdfPlan(
            pageSize(options),
            orientation(options),
            margin(options),
            properties.getFitImageDpi(),
            properties.getMaxFitPagePoints()
        );
    }

    private String pageSize(JsonNode options) {
        JsonNode node = options.get("pageSize");
        String value = node == null
            ? "fit"
            : node.asText("").toLowerCase(Locale.ROOT);
        if (node != null && !node.isTextual()
                || !PAGE_SIZES.contains(value)) {
            throw new OperationException(
                "INVALID_JPG_PDF_PAGE_SIZE",
                "pageSize must be one of "
                    + PAGE_SIZES.stream().sorted().toList()
            );
        }
        return value;
    }

    private String orientation(JsonNode options) {
        JsonNode node = options.get("orientation");
        String value = node == null
            ? "auto"
            : node.asText("").toLowerCase(Locale.ROOT);
        if (node != null && !node.isTextual()
                || !ORIENTATIONS.contains(value)) {
            throw new OperationException(
                "INVALID_JPG_PDF_ORIENTATION",
                "orientation must be one of "
                    + ORIENTATIONS.stream().sorted().toList()
            );
        }
        return value;
    }

    private float margin(JsonNode options) {
        JsonNode node = options.get("margin");
        double value = node == null ? 24 : node.asDouble(Double.NaN);
        if (node != null && !node.isNumber()
                || !Double.isFinite(value)
                || value < 0
                || value > 144) {
            throw new OperationException(
                "INVALID_JPG_PDF_MARGIN",
                "margin must be between 0 and 144 points"
            );
        }
        return (float) value;
    }

    public record JpgToPdfPlan(
        String pageSize,
        String orientation,
        float margin,
        int fitImageDpi,
        float maxFitPagePoints
    ) {
    }
}
