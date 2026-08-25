package com.pdftools.operations.pdfword;

import com.pdftools.operations.OperationException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfToWordPlanFactoryTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PdfToWordPlanFactory factory = new PdfToWordPlanFactory();

    @Test
    void suppliesEditableDefaults() throws Exception {
        PdfToWordPlanFactory.PdfToWordPlan plan = factory.create(
            objectMapper.readTree("{}")
        );

        assertEquals("editable", plan.mode());
        assertTrue(plan.includeImages());
        assertTrue(plan.detectTables());
        assertTrue(plan.preservePageBreaks());
    }

    @Test
    void parsesVisualAndEditableControls() throws Exception {
        PdfToWordPlanFactory.PdfToWordPlan plan = factory.create(
            objectMapper.readTree("""
                {
                  "mode":"visual",
                  "includeImages":false,
                  "detectTables":false,
                  "preservePageBreaks":false
                }
                """)
        );

        assertEquals("visual", plan.mode());
        assertFalse(plan.includeImages());
        assertFalse(plan.detectTables());
        assertFalse(plan.preservePageBreaks());
    }

    @Test
    void rejectsUnsupportedAndMistypedOptions() throws Exception {
        assertCode("INVALID_PDF_WORD_MODE", """
            {"mode":"perfect"}
            """);
        assertCode("INVALID_PDF_WORD_OPTIONS", """
            {"includeImages":"yes"}
            """);
        assertCode("INVALID_PDF_WORD_OPTIONS", """
            {"detectTables":1}
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
