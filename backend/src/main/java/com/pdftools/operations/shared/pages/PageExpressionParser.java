package com.pdftools.operations.shared.pages;

import com.pdftools.operations.OperationException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class PageExpressionParser {

    private static final int MAX_EXPRESSION_LENGTH = 4096;
    private static final int MAX_EXPANDED_SELECTIONS = 100_000;
    private static final Pattern PAGE = Pattern.compile("\\d+");
    private static final Pattern RANGE = Pattern.compile("(\\d*)\\s*-\\s*(\\d*)");

    public List<Integer> parse(String expression, int pageCount, DuplicatePolicy duplicatePolicy) {
        if (pageCount < 1) {
            throw error("INVALID_PAGE_COUNT", "The document must contain at least one page", null);
        }
        if (expression == null || expression.isBlank()) {
            throw error("EMPTY_PAGE_EXPRESSION", "Enter at least one page or range", null);
        }
        if (expression.length() > MAX_EXPRESSION_LENGTH) {
            throw error(
                "PAGE_EXPRESSION_TOO_LONG",
                "The page expression exceeds " + MAX_EXPRESSION_LENGTH + " characters",
                null
            );
        }

        List<Integer> pages = new ArrayList<>();
        Set<Integer> seen = new LinkedHashSet<>();
        long expandedSelections = 0;
        String[] tokens = expression.split(",", -1);
        for (String rawToken : tokens) {
            String token = rawToken.trim().toLowerCase(Locale.ROOT);
            if (token.isEmpty()) {
                throw error("INVALID_PAGE_TOKEN", "Page expressions cannot contain empty items", rawToken);
            }

            List<Integer> expanded = expand(token, pageCount);
            expandedSelections += expanded.size();
            if (expandedSelections > MAX_EXPANDED_SELECTIONS) {
                throw error(
                    "PAGE_SELECTION_TOO_LARGE",
                    "The page expression expands beyond "
                        + MAX_EXPANDED_SELECTIONS + " selections",
                    token
                );
            }
            for (int page : expanded) {
                if (!seen.add(page)) {
                    if (duplicatePolicy == DuplicatePolicy.REJECT) {
                        throw error(
                            "DUPLICATE_PAGE",
                            "Page " + page + " is selected more than once",
                            token
                        );
                    }
                    if (duplicatePolicy == DuplicatePolicy.DEDUPLICATE) {
                        continue;
                    }
                }
                pages.add(page);
            }
        }

        if (pages.isEmpty()) {
            throw error(
                "EMPTY_PAGE_SELECTION",
                "The page expression does not select any pages",
                expression
            );
        }
        return List.copyOf(pages);
    }

    private List<Integer> expand(String token, int pageCount) {
        return switch (token) {
            case "all" -> range(1, pageCount);
            case "odd" -> steppedRange(1, pageCount, 2);
            case "even" -> steppedRange(2, pageCount, 2);
            default -> expandNumericToken(token, pageCount);
        };
    }

    private List<Integer> expandNumericToken(String token, int pageCount) {
        if (PAGE.matcher(token).matches()) {
            return List.of(validatePage(parsePageNumber(token, token), pageCount, token));
        }

        Matcher matcher = RANGE.matcher(token);
        if (!matcher.matches() || matcher.group(1).isEmpty() && matcher.group(2).isEmpty()) {
            throw error("INVALID_PAGE_TOKEN", "Invalid page token: " + token, token);
        }

        int start = matcher.group(1).isEmpty()
            ? 1
            : parsePageNumber(matcher.group(1), token);
        int end = matcher.group(2).isEmpty()
            ? pageCount
            : parsePageNumber(matcher.group(2), token);
        validatePage(start, pageCount, token);
        validatePage(end, pageCount, token);
        if (start > end) {
            throw error(
                "DESCENDING_PAGE_RANGE",
                "Page range start must not exceed its end: " + token,
                token
            );
        }
        return range(start, end);
    }

    private int parsePageNumber(String value, String token) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw error(
                "INVALID_PAGE_NUMBER",
                "Page number is too large: " + value,
                token
            );
        }
    }

    private int validatePage(int page, int pageCount, String token) {
        if (page < 1 || page > pageCount) {
            throw error(
                "PAGE_OUT_OF_RANGE",
                "Page " + page + " is outside the valid range 1-" + pageCount,
                token
            );
        }
        return page;
    }

    private List<Integer> range(int start, int end) {
        return steppedRange(start, end, 1);
    }

    private List<Integer> steppedRange(int start, int end, int step) {
        long count = start > end ? 0 : ((long) end - start) / step + 1;
        if (count > MAX_EXPANDED_SELECTIONS) {
            throw error(
                "PAGE_SELECTION_TOO_LARGE",
                "A page range cannot expand beyond "
                    + MAX_EXPANDED_SELECTIONS + " selections",
                start + "-" + end
            );
        }
        List<Integer> pages = new ArrayList<>();
        long page = start;
        for (long index = 0; index < count; index++) {
            pages.add((int) page);
            page += step;
        }
        return pages;
    }

    private OperationException error(String code, String message, String token) {
        return new OperationException(
            code,
            message,
            token == null ? Map.of() : Map.of("token", token)
        );
    }
}
