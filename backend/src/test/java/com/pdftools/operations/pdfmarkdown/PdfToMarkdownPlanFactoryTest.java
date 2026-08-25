package com.pdftools.operations.pdfmarkdown;

import com.pdftools.operations.OperationException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfToMarkdownPlanFactoryTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PdfToMarkdownPlanFactory factory =
        new PdfToMarkdownPlanFactory();

    @Test
    void suppliesStructuredDefaults() throws Exception {
        var plan = factory.create(objectMapper.readTree("{}"));

        assertTrue(plan.detectHeadings());
        assertTrue(plan.detectLists());
        assertTrue(plan.detectTables());
        assertTrue(plan.includeImages());
        assertTrue(plan.preservePageBreaks());
    }

    @Test
    void parsesDisabledStructureOptions() throws Exception {
        var plan = factory.create(objectMapper.readTree("""
            {
              "detectHeadings":false,
              "detectLists":false,
              "detectTables":false,
              "includeImages":false,
              "preservePageBreaks":false
            }
            """));

        assertFalse(plan.detectHeadings());
        assertFalse(plan.detectLists());
        assertFalse(plan.detectTables());
        assertFalse(plan.includeImages());
        assertFalse(plan.preservePageBreaks());
    }

    @Test
    void rejectsMistypedOptions() throws Exception {
        assertThrows(
            OperationException.class,
            () -> factory.create(objectMapper.readTree("""
                {"detectTables":"yes"}
                """))
        );
    }
}
