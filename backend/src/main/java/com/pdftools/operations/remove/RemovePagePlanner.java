package com.pdftools.operations.remove;

import com.pdftools.operations.OperationException;
import com.pdftools.operations.shared.pages.DuplicatePolicy;
import com.pdftools.operations.shared.pages.PageExpressionParser;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class RemovePagePlanner {

    private final PageExpressionParser pageExpressionParser;

    public RemovePagePlanner(PageExpressionParser pageExpressionParser) {
        this.pageExpressionParser = pageExpressionParser;
    }

    public List<Integer> remainingPages(
            String expression,
            int pageCount) {
        List<Integer> removed = pageExpressionParser.parse(
            expression,
            pageCount,
            DuplicatePolicy.REJECT
        );
        if (removed.size() == pageCount) {
            throw new OperationException(
                "CANNOT_REMOVE_ALL_PAGES",
                "At least one page must remain in the PDF"
            );
        }
        Set<Integer> removedSet = new HashSet<>(removed);
        List<Integer> remaining = new ArrayList<>(
            pageCount - removed.size()
        );
        for (int page = 1; page <= pageCount; page++) {
            if (!removedSet.contains(page)) {
                remaining.add(page);
            }
        }
        return List.copyOf(remaining);
    }
}
