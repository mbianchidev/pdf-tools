package com.pdftools.operations.htmlpdf;

import com.pdftools.operations.OperationException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HtmlPlanFactoryTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HtmlPlanFactory factory = new HtmlPlanFactory();

    @Test
    void suppliesSafePrintDefaults() throws Exception {
        HtmlPlanFactory.HtmlPlan plan = factory.create(
            objectMapper.readTree("{}")
        );

        assertEquals("A4", plan.pageSize());
        assertFalse(plan.landscape());
        assertTrue(plan.printBackground());
        assertEquals(10, plan.marginMm());
    }

    @Test
    void parsesSupportedPrintControls() throws Exception {
        HtmlPlanFactory.HtmlPlan plan = factory.create(
            objectMapper.readTree("""
                {
                  "pageSize": "letter",
                  "orientation": "landscape",
                  "printBackground": false,
                  "marginMm": 24
                }
                """)
        );

        assertEquals("Letter", plan.pageSize());
        assertTrue(plan.landscape());
        assertFalse(plan.printBackground());
        assertEquals(24, plan.marginMm());
    }

    @Test
    void rejectsUnsupportedOrMistypedControls() throws Exception {
        assertCode("INVALID_HTML_PAGE_SIZE", """
            {"pageSize":"tabloid"}
            """);
        assertCode("INVALID_HTML_ORIENTATION", """
            {"orientation":"diagonal"}
            """);
        assertCode("INVALID_HTML_OPTIONS", """
            {"printBackground":"yes"}
            """);
        assertCode("INVALID_HTML_MARGIN", """
            {"marginMm":51}
            """);
    }

    private void assertCode(String code, String options) throws Exception {
        OperationException exception = assertThrows(
            OperationException.class,
            () -> factory.create(objectMapper.readTree(options))
        );
        assertEquals(code, exception.getCode());
    }
}
