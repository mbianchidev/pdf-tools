package com.pdftools.operations.split;

import com.pdftools.operations.OperationException;
import com.pdftools.operations.shared.pages.DuplicatePolicy;
import com.pdftools.operations.shared.pages.PageExpressionParser;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class SplitPlanFactory {

    private final PageExpressionParser pageExpressionParser;
    private final SplitProperties properties;

    public SplitPlanFactory(
            PageExpressionParser pageExpressionParser,
            SplitProperties properties) {
        this.pageExpressionParser = pageExpressionParser;
        this.properties = properties;
    }

    public List<SplitGroup> create(JsonNode options, int pageCount) {
        if (pageCount < 1) {
            throw new OperationException("EMPTY_PDF", "The PDF does not contain any pages");
        }
        if (pageCount > properties.getMaxPages()) {
            throw new OperationException(
                "PDF_PAGE_LIMIT_EXCEEDED",
                "The PDF exceeds the split page limit",
                Map.of("maxPages", properties.getMaxPages())
            );
        }

        SplitMode mode = SplitMode.parse(options.path("mode").asText("individual"));
        List<SplitGroup> groups = switch (mode) {
            case INDIVIDUAL -> individual(pageCount);
            case FIXED -> fixed(options, pageCount);
            case RANGES -> ranges(options, pageCount);
        };
        if (groups.size() > properties.getMaxOutputs()) {
            throw new OperationException(
                "SPLIT_OUTPUT_LIMIT_EXCEEDED",
                "Split would create too many output documents",
                Map.of("maxOutputs", properties.getMaxOutputs())
            );
        }
        return List.copyOf(groups);
    }

    private List<SplitGroup> individual(int pageCount) {
        List<SplitGroup> groups = new ArrayList<>(pageCount);
        for (int page = 1; page <= pageCount; page++) {
            groups.add(new SplitGroup(page, List.of(page)));
        }
        return groups;
    }

    private List<SplitGroup> fixed(JsonNode options, int pageCount) {
        JsonNode groupSizeNode = options.get("fixedGroupSize");
        if (groupSizeNode == null || !groupSizeNode.canConvertToInt()) {
            throw new OperationException(
                "INVALID_FIXED_GROUP_SIZE",
                "fixedGroupSize must be an integer"
            );
        }
        int groupSize = groupSizeNode.asInt();
        if (groupSize < 1 || groupSize > properties.getMaxFixedGroupSize()) {
            throw new OperationException(
                "INVALID_FIXED_GROUP_SIZE",
                "fixedGroupSize must be between 1 and "
                    + properties.getMaxFixedGroupSize(),
                Map.of("maxFixedGroupSize", properties.getMaxFixedGroupSize())
            );
        }

        List<SplitGroup> groups = new ArrayList<>();
        int position = 1;
        for (int start = 1; start <= pageCount; start += groupSize) {
            int end = Math.min(pageCount, start + groupSize - 1);
            List<Integer> pages = new ArrayList<>(end - start + 1);
            for (int page = start; page <= end; page++) {
                pages.add(page);
            }
            groups.add(new SplitGroup(position++, pages));
        }
        return groups;
    }

    private List<SplitGroup> ranges(JsonNode options, int pageCount) {
        JsonNode ranges = options.get("ranges");
        if (ranges == null || !ranges.isArray() || ranges.isEmpty()) {
            throw new OperationException(
                "SPLIT_RANGES_REQUIRED",
                "ranges mode requires at least one page expression"
            );
        }
        if (ranges.size() > properties.getMaxOutputs()) {
            throw new OperationException(
                "SPLIT_OUTPUT_LIMIT_EXCEEDED",
                "Too many split ranges",
                Map.of("maxOutputs", properties.getMaxOutputs())
            );
        }

        Set<Integer> assignedPages = new HashSet<>();
        List<SplitGroup> groups = new ArrayList<>();
        for (int index = 0; index < ranges.size(); index++) {
            JsonNode range = ranges.get(index);
            if (!range.isTextual() || range.asText().isBlank()) {
                throw new OperationException(
                    "INVALID_SPLIT_RANGE",
                    "Every split range must be a non-empty page expression",
                    Map.of("position", index + 1)
                );
            }
            List<Integer> pages = pageExpressionParser.parse(
                range.asText(),
                pageCount,
                DuplicatePolicy.REJECT
            );
            for (int page : pages) {
                if (!assignedPages.add(page)) {
                    throw new OperationException(
                        "OVERLAPPING_SPLIT_RANGES",
                        "Page " + page + " appears in more than one split range",
                        Map.of("page", page, "position", index + 1)
                    );
                }
            }
            groups.add(new SplitGroup(index + 1, pages));
        }
        return groups;
    }
}
