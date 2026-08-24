package com.pdftools.operations.pagenumbers;

import com.pdftools.operations.OperationException;
import com.pdftools.operations.shared.pages.DuplicatePolicy;
import com.pdftools.operations.shared.pages.PageExpressionParser;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class PageNumbersPlanFactory {

    private static final int MAX_TEMPLATE_LENGTH = 100;
    private static final int MAX_START = 1_000_000;
    private static final Set<String> POSITIONS = Set.of(
        "top-left",
        "top-center",
        "top-right",
        "bottom-left",
        "bottom-center",
        "bottom-right"
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

    public PageNumbersPlanFactory(
            PageExpressionParser pageExpressionParser) {
        this.pageExpressionParser = pageExpressionParser;
    }

    public void validateShape(JsonNode options) {
        template(options);
        start(options);
        font(options);
        fontSize(options);
        position(options);
        margin(options);
        if (options.has("pages")) {
            JsonNode pages = options.get("pages");
            if (!pages.isTextual() || pages.asText().isBlank()) {
                throw new OperationException(
                    "INVALID_PAGE_NUMBER_PAGES",
                    "pages must be a non-empty page-expression string"
                );
            }
        }
    }

    public PageNumbersPlan create(JsonNode options, int pageCount) {
        validateShape(options);
        List<Integer> selected = pageExpressionParser.parse(
            options.path("pages").asText("all"),
            pageCount,
            DuplicatePolicy.REJECT
        ).stream().sorted().toList();
        int start = start(options);
        Map<Integer, Integer> numbers = new HashMap<>();
        for (int index = 0; index < selected.size(); index++) {
            numbers.put(selected.get(index), start + index);
        }
        return new PageNumbersPlan(
            Map.copyOf(numbers),
            template(options),
            font(options),
            fontSize(options),
            position(options),
            margin(options),
            pageCount
        );
    }

    private String template(JsonNode options) {
        JsonNode templateNode = options.get("template");
        String template = templateNode == null
            ? "{page}"
            : templateNode.asText("");
        String stripped = template
            .replace("{page}", "")
            .replace("{total}", "")
            .replace("{source}", "");
        boolean printableAscii = template.chars()
            .allMatch(character -> character >= 32 && character <= 126);
        if (!templateNodeIsTextual(templateNode)
                || template.isBlank()
                || template.length() > MAX_TEMPLATE_LENGTH
                || !template.contains("{page}")
                || stripped.contains("{")
                || stripped.contains("}")
                || !printableAscii) {
            throw new OperationException(
                "INVALID_PAGE_NUMBER_TEMPLATE",
                "template must be printable ASCII, include {page}, use only "
                    + "{page}, {total}, and {source}, and stay within "
                    + MAX_TEMPLATE_LENGTH
                    + " characters"
            );
        }
        return template;
    }

    private boolean templateNodeIsTextual(JsonNode node) {
        return node == null || node.isTextual();
    }

    private int start(JsonNode options) {
        JsonNode node = options.get("start");
        if (node == null) {
            return 1;
        }
        if (!node.isIntegralNumber()
                || !node.canConvertToInt()
                || node.asInt() < 0
                || node.asInt() > MAX_START) {
            throw new OperationException(
                "INVALID_PAGE_NUMBER_START",
                "start must be an integer between 0 and " + MAX_START
            );
        }
        return node.asInt();
    }

    private Standard14Fonts.FontName font(JsonNode options) {
        JsonNode node = options.get("font");
        String name = node == null
            ? "helvetica"
            : node.asText("").toLowerCase(Locale.ROOT);
        if ((node != null && !node.isTextual())
                || !FONTS.containsKey(name)) {
            throw new OperationException(
                "INVALID_PAGE_NUMBER_FONT",
                "font must be one of " + FONTS.keySet().stream()
                    .sorted()
                    .toList()
            );
        }
        return FONTS.get(name);
    }

    private float fontSize(JsonNode options) {
        JsonNode node = options.get("fontSize");
        double value = node == null ? 12 : node.asDouble(Double.NaN);
        if ((node != null && !node.isNumber())
                || !Double.isFinite(value)
                || value < 6
                || value > 72) {
            throw new OperationException(
                "INVALID_PAGE_NUMBER_FONT_SIZE",
                "fontSize must be between 6 and 72"
            );
        }
        return (float) value;
    }

    private String position(JsonNode options) {
        JsonNode node = options.get("position");
        String value = node == null ? "bottom-center" : node.asText("");
        if ((node != null && !node.isTextual())
                || !POSITIONS.contains(value)) {
            throw new OperationException(
                "INVALID_PAGE_NUMBER_POSITION",
                "position must be one of "
                    + POSITIONS.stream().sorted().toList()
            );
        }
        return value;
    }

    private float margin(JsonNode options) {
        JsonNode node = options.get("margin");
        double value = node == null ? 24 : node.asDouble(Double.NaN);
        if ((node != null && !node.isNumber())
                || !Double.isFinite(value)
                || value < 0
                || value > 144) {
            throw new OperationException(
                "INVALID_PAGE_NUMBER_MARGIN",
                "margin must be between 0 and 144 points"
            );
        }
        return (float) value;
    }

    public record PageNumbersPlan(
        Map<Integer, Integer> numbers,
        String template,
        Standard14Fonts.FontName font,
        float fontSize,
        String position,
        float margin,
        int totalPages
    ) {
        public Integer numberFor(int sourcePage) {
            return numbers.get(sourcePage);
        }

        public String textFor(int sourcePage) {
            Integer number = numberFor(sourcePage);
            if (number == null) {
                return null;
            }
            return template
                .replace("{page}", String.valueOf(number))
                .replace("{total}", String.valueOf(totalPages))
                .replace("{source}", String.valueOf(sourcePage));
        }
    }
}
