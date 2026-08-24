package com.pdftools.operations.watermark;

import com.pdftools.operations.OperationException;
import com.pdftools.operations.shared.pages.DuplicatePolicy;
import com.pdftools.operations.shared.pages.PageExpressionParser;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class WatermarkPlanFactory {

    private static final Pattern COLOR = Pattern.compile(
        "#[0-9a-fA-F]{6}"
    );
    private static final Map<String, Standard14Fonts.FontName> FONTS = Map.of(
        "helvetica", Standard14Fonts.FontName.HELVETICA,
        "helvetica-bold", Standard14Fonts.FontName.HELVETICA_BOLD,
        "times", Standard14Fonts.FontName.TIMES_ROMAN,
        "times-bold", Standard14Fonts.FontName.TIMES_BOLD,
        "courier", Standard14Fonts.FontName.COURIER,
        "courier-bold", Standard14Fonts.FontName.COURIER_BOLD
    );

    private final PageExpressionParser pageExpressionParser;

    public WatermarkPlanFactory(
            PageExpressionParser pageExpressionParser) {
        this.pageExpressionParser = pageExpressionParser;
    }

    public WatermarkMode mode(JsonNode options) {
        JsonNode node = options.get("mode");
        String value = node == null
            ? "text"
            : node.asText("").toLowerCase(Locale.ROOT);
        if (node != null && !node.isTextual()) {
            throw invalidMode();
        }
        return switch (value) {
            case "text" -> WatermarkMode.TEXT;
            case "image" -> WatermarkMode.IMAGE;
            default -> throw invalidMode();
        };
    }

    public void validateShape(JsonNode options) {
        WatermarkMode mode = mode(options);
        pages(options);
        opacity(options);
        rotation(options);
        coordinate(options, "x");
        coordinate(options, "y");
        if (mode == WatermarkMode.TEXT) {
            text(options);
            font(options);
            fontSize(options);
            color(options);
        } else {
            imageWidthPercent(options);
        }
    }

    public WatermarkPlan create(JsonNode options, int pageCount) {
        validateShape(options);
        Set<Integer> selected = Set.copyOf(
            pageExpressionParser.parse(
                pages(options),
                pageCount,
                DuplicatePolicy.REJECT
            )
        );
        WatermarkMode mode = mode(options);
        return new WatermarkPlan(
            mode,
            selected,
            opacity(options),
            rotation(options),
            coordinate(options, "x"),
            coordinate(options, "y"),
            mode == WatermarkMode.TEXT ? text(options) : null,
            mode == WatermarkMode.TEXT ? font(options) : null,
            mode == WatermarkMode.TEXT ? fontSize(options) : 0,
            mode == WatermarkMode.TEXT ? color(options) : null,
            mode == WatermarkMode.IMAGE
                ? imageWidthPercent(options)
                : 0
        );
    }

    private String pages(JsonNode options) {
        JsonNode node = options.get("pages");
        if (node == null) {
            return "all";
        }
        if (!node.isTextual() || node.asText().isBlank()) {
            throw new OperationException(
                "INVALID_WATERMARK_PAGES",
                "pages must be a non-empty page-expression string"
            );
        }
        return node.asText();
    }

    private float opacity(JsonNode options) {
        JsonNode node = options.get("opacity");
        double value = node == null ? 0.3 : node.asDouble(Double.NaN);
        if (node != null && !node.isNumber()
                || !Double.isFinite(value)
                || value < 0.05
                || value > 1) {
            throw new OperationException(
                "INVALID_WATERMARK_OPACITY",
                "opacity must be between 0.05 and 1"
            );
        }
        return (float) value;
    }

    private float rotation(JsonNode options) {
        JsonNode node = options.get("rotation");
        double value = node == null ? 45 : node.asDouble(Double.NaN);
        if (node != null && !node.isNumber()
                || !Double.isFinite(value)
                || value < -180
                || value > 180) {
            throw new OperationException(
                "INVALID_WATERMARK_ROTATION",
                "rotation must be between -180 and 180 degrees"
            );
        }
        return (float) value;
    }

    private float coordinate(JsonNode options, String field) {
        JsonNode node = options.get(field);
        double value = node == null ? 0.5 : node.asDouble(Double.NaN);
        if (node != null && !node.isNumber()
                || !Double.isFinite(value)
                || value < 0
                || value > 1) {
            throw new OperationException(
                "INVALID_WATERMARK_POSITION",
                field + " must be between 0 and 1"
            );
        }
        return (float) value;
    }

    private String text(JsonNode options) {
        JsonNode node = options.get("text");
        String value = node == null ? "" : node.asText("");
        boolean printable = value.chars()
            .allMatch(character -> character >= 32 && character <= 126);
        if (node == null
                || !node.isTextual()
                || value.isBlank()
                || value.length() > 100
                || !printable) {
            throw new OperationException(
                "WATERMARK_TEXT_REQUIRED",
                "text must be printable ASCII within 100 characters"
            );
        }
        return value;
    }

    private Standard14Fonts.FontName font(JsonNode options) {
        JsonNode node = options.get("font");
        String value = node == null
            ? "helvetica-bold"
            : node.asText("").toLowerCase(Locale.ROOT);
        if (node != null && !node.isTextual()
                || !FONTS.containsKey(value)) {
            throw new OperationException(
                "INVALID_WATERMARK_FONT",
                "font must be one of "
                    + FONTS.keySet().stream().sorted().toList()
            );
        }
        return FONTS.get(value);
    }

    private float fontSize(JsonNode options) {
        JsonNode node = options.get("fontSize");
        double value = node == null ? 48 : node.asDouble(Double.NaN);
        if (node != null && !node.isNumber()
                || !Double.isFinite(value)
                || value < 8
                || value > 144) {
            throw new OperationException(
                "INVALID_WATERMARK_FONT_SIZE",
                "fontSize must be between 8 and 144 points"
            );
        }
        return (float) value;
    }

    private RgbColor color(JsonNode options) {
        JsonNode node = options.get("color");
        String value = node == null ? "#4f46e5" : node.asText("");
        if (node != null && !node.isTextual()
                || !COLOR.matcher(value).matches()) {
            throw new OperationException(
                "INVALID_WATERMARK_COLOR",
                "color must use #RRGGBB"
            );
        }
        return new RgbColor(
            Integer.parseInt(value.substring(1, 3), 16),
            Integer.parseInt(value.substring(3, 5), 16),
            Integer.parseInt(value.substring(5, 7), 16)
        );
    }

    private float imageWidthPercent(JsonNode options) {
        JsonNode node = options.get("imageWidthPercent");
        double value = node == null ? 35 : node.asDouble(Double.NaN);
        if (node != null && !node.isNumber()
                || !Double.isFinite(value)
                || value < 5
                || value > 100) {
            throw new OperationException(
                "INVALID_WATERMARK_IMAGE_SIZE",
                "imageWidthPercent must be between 5 and 100"
            );
        }
        return (float) value;
    }

    private OperationException invalidMode() {
        return new OperationException(
            "INVALID_WATERMARK_MODE",
            "mode must be text or image"
        );
    }

    public enum WatermarkMode {
        TEXT,
        IMAGE
    }

    public record WatermarkPlan(
        WatermarkMode mode,
        Set<Integer> pages,
        float opacity,
        float rotation,
        float x,
        float y,
        String text,
        Standard14Fonts.FontName font,
        float fontSize,
        RgbColor color,
        float imageWidthPercent
    ) {
        public WatermarkPlan {
            pages = Set.copyOf(pages);
        }

        public boolean includes(int pageNumber) {
            return pages.contains(pageNumber);
        }
    }

    public record RgbColor(int red, int green, int blue) {
    }
}
