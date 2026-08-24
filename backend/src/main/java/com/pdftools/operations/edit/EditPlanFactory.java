package com.pdftools.operations.edit;

import com.pdftools.operations.OperationException;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class EditPlanFactory {

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

    private final EditProperties properties;

    public EditPlanFactory(EditProperties properties) {
        this.properties = properties;
    }

    public void validateShape(JsonNode options, int imageCount) {
        elements(options, Integer.MAX_VALUE, imageCount);
    }

    public EditPlan create(
            JsonNode options,
            int pageCount,
            int imageCount) {
        return new EditPlan(elements(options, pageCount, imageCount));
    }

    private List<EditElement> elements(
            JsonNode options,
            int pageCount,
            int imageCount) {
        JsonNode nodes = options.get("elements");
        if (nodes == null || !nodes.isArray() || nodes.isEmpty()) {
            throw new OperationException(
                "EDIT_ELEMENTS_REQUIRED",
                "elements must contain at least one edit"
            );
        }
        if (nodes.size() > properties.getMaxElements()) {
            throw new OperationException(
                "EDIT_ELEMENT_LIMIT_EXCEEDED",
                "Use at most "
                    + properties.getMaxElements()
                    + " edit elements"
            );
        }
        List<EditElement> result = new ArrayList<>(nodes.size());
        for (JsonNode node : nodes) {
            if (!node.isObject()) {
                throw invalidElement();
            }
            String type = text(node, "type", "");
            result.add(switch (type) {
                case "text" -> textElement(node, pageCount);
                case "image" -> imageElement(
                    node,
                    pageCount,
                    imageCount
                );
                case "rectangle" -> shapeElement(
                    node,
                    pageCount,
                    false
                );
                case "ellipse" -> shapeElement(
                    node,
                    pageCount,
                    true
                );
                case "line" -> lineElement(node, pageCount);
                case "highlight" -> highlightElement(node, pageCount);
                case "note" -> noteElement(node, pageCount);
                default -> throw invalidElement();
            });
        }
        Set<Integer> referencedImages = new HashSet<>();
        for (EditElement element : result) {
            if (element instanceof ImageElement image) {
                referencedImages.add(image.imageIndex());
            }
        }
        if (referencedImages.size() != imageCount) {
            throw new OperationException(
                "UNUSED_EDIT_IMAGE",
                "Every uploaded edit image must be referenced"
            );
        }
        return List.copyOf(result);
    }

    private TextElement textElement(JsonNode node, int pageCount) {
        String value = text(node, "text", "");
        boolean printable = value.chars()
            .allMatch(character -> character >= 32 && character <= 126);
        if (value.isBlank() || value.length() > 200 || !printable) {
            throw new OperationException(
                "INVALID_EDIT_TEXT",
                "Text elements require printable ASCII within 200 characters"
            );
        }
        return new TextElement(
            page(node, pageCount),
            coordinate(node, "x", 0.5),
            coordinate(node, "y", 0.5),
            value,
            font(node),
            number(node, "fontSize", 24, 8, 144),
            color(node, "color", "#111111"),
            opacity(node),
            number(node, "rotation", 0, -180, 180)
        );
    }

    private ImageElement imageElement(
            JsonNode node,
            int pageCount,
            int imageCount) {
        JsonNode indexNode = node.get("imageIndex");
        if (indexNode == null
                || !indexNode.isIntegralNumber()
                || !indexNode.canConvertToInt()
                || indexNode.asInt() < 0
                || indexNode.asInt() >= imageCount) {
            throw new OperationException(
                "INVALID_EDIT_IMAGE_INDEX",
                "imageIndex must reference an uploaded image"
            );
        }
        return new ImageElement(
            page(node, pageCount),
            coordinate(node, "x", 0.5),
            coordinate(node, "y", 0.5),
            number(node, "width", 0.3, 0.02, 1),
            indexNode.asInt(),
            opacity(node),
            number(node, "rotation", 0, -180, 180)
        );
    }

    private ShapeElement shapeElement(
            JsonNode node,
            int pageCount,
            boolean ellipse) {
        return new ShapeElement(
            ellipse ? ElementType.ELLIPSE : ElementType.RECTANGLE,
            page(node, pageCount),
            coordinate(node, "x", 0.25),
            coordinate(node, "y", 0.25),
            number(node, "width", 0.25, 0.01, 1),
            number(node, "height", 0.15, 0.01, 1),
            color(node, "strokeColor", "#4f46e5"),
            optionalColor(node, "fillColor"),
            number(node, "strokeWidth", 2, 0.1, 20),
            opacity(node)
        );
    }

    private LineElement lineElement(JsonNode node, int pageCount) {
        return new LineElement(
            page(node, pageCount),
            coordinate(node, "x", 0.25),
            coordinate(node, "y", 0.5),
            coordinate(node, "x2", 0.75),
            coordinate(node, "y2", 0.5),
            color(node, "color", "#4f46e5"),
            number(node, "strokeWidth", 2, 0.1, 20),
            opacity(node)
        );
    }

    private HighlightElement highlightElement(
            JsonNode node,
            int pageCount) {
        float width = number(node, "width", 0.6, 0.01, 1);
        float height = number(node, "height", 0.08, 0.01, 1);
        float x = Math.min(coordinate(node, "x", 0.2), 1 - width);
        float y = Math.min(coordinate(node, "y", 0.4), 1 - height);
        return new HighlightElement(
            page(node, pageCount),
            x,
            y,
            width,
            height,
            color(node, "color", "#fff176"),
            number(node, "opacity", 0.35, 0.05, 1)
        );
    }

    private NoteElement noteElement(JsonNode node, int pageCount) {
        String contents = text(node, "contents", "");
        if (contents.isBlank() || contents.length() > 1000) {
            throw new OperationException(
                "INVALID_EDIT_NOTE",
                "Note contents must stay within 1000 characters"
            );
        }
        String title = text(node, "title", "PDF Tools");
        if (title.length() > 100) {
            throw new OperationException(
                "INVALID_EDIT_NOTE",
                "Note title must stay within 100 characters"
            );
        }
        return new NoteElement(
            page(node, pageCount),
            coordinate(node, "x", 0.5),
            coordinate(node, "y", 0.5),
            contents,
            title,
            color(node, "color", "#ffb300")
        );
    }

    private int page(JsonNode node, int pageCount) {
        JsonNode value = node.get("page");
        if (value == null
                || !value.isIntegralNumber()
                || !value.canConvertToInt()
                || value.asInt() < 1
                || value.asInt() > pageCount) {
            throw new OperationException(
                "EDIT_PAGE_OUT_OF_RANGE",
                "Every edit page must be within the document"
            );
        }
        return value.asInt();
    }

    private float coordinate(
            JsonNode node,
            String field,
            double fallback) {
        return number(node, field, fallback, 0, 1);
    }

    private float opacity(JsonNode node) {
        return number(node, "opacity", 1, 0.05, 1);
    }

    private float number(
            JsonNode node,
            String field,
            double fallback,
            double minimum,
            double maximum) {
        JsonNode value = node.get(field);
        double resolved = value == null
            ? fallback
            : value.asDouble(Double.NaN);
        if (value != null && !value.isNumber()
                || !Double.isFinite(resolved)
                || resolved < minimum
                || resolved > maximum) {
            throw invalidElement();
        }
        return (float) resolved;
    }

    private Standard14Fonts.FontName font(JsonNode node) {
        String value = text(node, "font", "helvetica");
        Standard14Fonts.FontName font = FONTS.get(
            value.toLowerCase(Locale.ROOT)
        );
        if (font == null) {
            throw invalidElement();
        }
        return font;
    }

    private RgbColor optionalColor(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()
                || value.isTextual()
                    && value.asText().equalsIgnoreCase("none")) {
            return null;
        }
        return color(node, field, null);
    }

    private RgbColor color(
            JsonNode node,
            String field,
            String fallback) {
        JsonNode value = node.get(field);
        String resolved = value == null ? fallback : value.asText("");
        if (resolved == null
                || value != null && !value.isTextual()
                || !COLOR.matcher(resolved).matches()) {
            throw invalidElement();
        }
        return new RgbColor(
            Integer.parseInt(resolved.substring(1, 3), 16),
            Integer.parseInt(resolved.substring(3, 5), 16),
            Integer.parseInt(resolved.substring(5, 7), 16)
        );
    }

    private String text(JsonNode node, String field, String fallback) {
        JsonNode value = node.get(field);
        if (value == null) {
            return fallback;
        }
        if (!value.isTextual()) {
            throw invalidElement();
        }
        return value.asText();
    }

    private OperationException invalidElement() {
        return new OperationException(
            "INVALID_EDIT_ELEMENT",
            "An edit element contains unsupported fields or values"
        );
    }

    public record EditPlan(List<EditElement> elements) {
        public EditPlan {
            elements = List.copyOf(elements);
        }

        public List<EditElement> forPage(int pageNumber) {
            return elements.stream()
                .filter(element -> element.page() == pageNumber)
                .toList();
        }
    }

    public sealed interface EditElement permits
            TextElement,
            ImageElement,
            ShapeElement,
            LineElement,
            HighlightElement,
            NoteElement {
        int page();
    }

    public record TextElement(
        int page,
        float x,
        float y,
        String text,
        Standard14Fonts.FontName font,
        float fontSize,
        RgbColor color,
        float opacity,
        float rotation
    ) implements EditElement {
    }

    public record ImageElement(
        int page,
        float x,
        float y,
        float width,
        int imageIndex,
        float opacity,
        float rotation
    ) implements EditElement {
    }

    public record ShapeElement(
        ElementType type,
        int page,
        float x,
        float y,
        float width,
        float height,
        RgbColor strokeColor,
        RgbColor fillColor,
        float strokeWidth,
        float opacity
    ) implements EditElement {
    }

    public record LineElement(
        int page,
        float x,
        float y,
        float x2,
        float y2,
        RgbColor color,
        float strokeWidth,
        float opacity
    ) implements EditElement {
    }

    public record HighlightElement(
        int page,
        float x,
        float y,
        float width,
        float height,
        RgbColor color,
        float opacity
    ) implements EditElement {
    }

    public record NoteElement(
        int page,
        float x,
        float y,
        String contents,
        String title,
        RgbColor color
    ) implements EditElement {
    }

    public record RgbColor(int red, int green, int blue) {
    }

    public enum ElementType {
        RECTANGLE,
        ELLIPSE
    }
}
