package com.pdftools.operations.organize;

import com.pdftools.operations.OperationException;
import com.pdftools.operations.split.SplitProperties;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class OrganizePlanFactory {

    private static final Set<Integer> ALLOWED_ROTATIONS =
        Set.of(0, 90, 180, 270);

    private final SplitProperties properties;

    public OrganizePlanFactory(SplitProperties properties) {
        this.properties = properties;
    }

    public void validateShape(JsonNode options) {
        JsonNode pages = options.get("pages");
        if (pages == null || !pages.isArray() || pages.isEmpty()) {
            throw new OperationException(
                "ORGANIZE_PAGES_REQUIRED",
                "pages must contain at least one output page"
            );
        }
        if (pages.size() > properties.getMaxPages()) {
            throw new OperationException(
                "PDF_PAGE_LIMIT_EXCEEDED",
                "The organized PDF exceeds the page limit",
                Map.of("maxPages", properties.getMaxPages())
            );
        }
        for (JsonNode page : pages) {
            if (!page.isObject()) {
                throw invalidPage();
            }
            JsonNode sourcePage = page.get("page");
            JsonNode rotation = page.get("rotation");
            if (sourcePage == null
                    || !sourcePage.isIntegralNumber()
                    || !sourcePage.canConvertToInt()
                    || sourcePage.asInt() < 1
                    || rotation == null
                    || !rotation.isIntegralNumber()
                    || !rotation.canConvertToInt()
                    || !ALLOWED_ROTATIONS.contains(rotation.asInt())) {
                throw invalidPage();
            }
        }
    }

    public OrganizePlan create(JsonNode options, int pageCount) {
        validateShape(options);
        List<OrganizedPage> pages = new ArrayList<>(
            options.get("pages").size()
        );
        for (int index = 0; index < options.get("pages").size(); index++) {
            JsonNode page = options.get("pages").get(index);
            int sourcePage = page.get("page").asInt();
            if (sourcePage > pageCount) {
                throw new OperationException(
                    "PAGE_OUT_OF_RANGE",
                    "Page " + sourcePage
                        + " is outside the valid range 1-"
                        + pageCount,
                    Map.of(
                        "page", sourcePage,
                        "position", index + 1
                    )
                );
            }
            pages.add(new OrganizedPage(
                sourcePage,
                page.get("rotation").asInt()
            ));
        }
        return new OrganizePlan(List.copyOf(pages));
    }

    private OperationException invalidPage() {
        return new OperationException(
            "INVALID_ORGANIZE_PAGE",
            "Every output page requires a positive page and rotation "
                + "of 0, 90, 180, or 270"
        );
    }

    public record OrganizePlan(List<OrganizedPage> pages) {
    }

    public record OrganizedPage(int sourcePage, int rotation) {
    }
}
