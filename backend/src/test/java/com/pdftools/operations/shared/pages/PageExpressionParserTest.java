package com.pdftools.operations.shared.pages;

import com.pdftools.operations.OperationException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PageExpressionParserTest {

    private final PageExpressionParser parser = new PageExpressionParser();

    @Test
    void parsesPagesRangesKeywordsAndOpenRangesInOrder() {
        assertEquals(
            List.of(1, 3, 4, 5, 2, 6, 8, 10, 7, 9),
            parser.parse("1, 3-5, even, 7-", 10, DuplicatePolicy.DEDUPLICATE)
        );
    }

    @Test
    void keepsDuplicatesWhenRequested() {
        assertEquals(
            List.of(1, 2, 2, 3),
            parser.parse("1-2,2-3", 3, DuplicatePolicy.KEEP)
        );
    }

    @Test
    void rejectsDuplicatePagesWhenRequested() {
        OperationException exception = assertThrows(
            OperationException.class,
            () -> parser.parse("1-3,3", 5, DuplicatePolicy.REJECT)
        );

        assertEquals("DUPLICATE_PAGE", exception.getCode());
    }

    @Test
    void rejectsDescendingAndOutOfRangeSelections() {
        assertEquals(
            "DESCENDING_PAGE_RANGE",
            assertThrows(
                OperationException.class,
                () -> parser.parse("4-2", 5, DuplicatePolicy.REJECT)
            ).getCode()
        );
        assertEquals(
            "PAGE_OUT_OF_RANGE",
            assertThrows(
                OperationException.class,
                () -> parser.parse("6", 5, DuplicatePolicy.REJECT)
            ).getCode()
        );
    }

    @Test
    void reportsIntegerOverflowAsAValidationError() {
        assertEquals(
            "INVALID_PAGE_NUMBER",
            assertThrows(
                OperationException.class,
                () -> parser.parse(
                    "999999999999999999999999",
                    5,
                    DuplicatePolicy.REJECT
                )
            ).getCode()
        );
    }

    @Test
    void boundsExpandedSelectionsBeforeTheyExhaustMemory() {
        String repeatedAll = String.join(",", java.util.Collections.nCopies(11, "all"));

        assertEquals(
            "PAGE_SELECTION_TOO_LARGE",
            assertThrows(
                OperationException.class,
                () -> parser.parse(repeatedAll, 10_000, DuplicatePolicy.KEEP)
            ).getCode()
        );
    }

    @Test
    void doesNotOverflowAtTheMaximumIntegerPage() {
        assertEquals(
            List.of(Integer.MAX_VALUE),
            parser.parse(
                Integer.MAX_VALUE + "-",
                Integer.MAX_VALUE,
                DuplicatePolicy.KEEP
            )
        );
    }
}
