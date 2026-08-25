package com.pdftools.operations.pdfppt;

import com.pdftools.operations.OperationException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfToPowerPointPlanFactoryTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PdfToPowerPointPlanFactory factory =
        new PdfToPowerPointPlanFactory();

    @Test
    void suppliesEditableSourceDefaults() throws Exception {
        var plan = factory.create(objectMapper.readTree("{}"));

        assertEquals("editable", plan.mode());
        assertEquals("source", plan.slideSize());
        assertTrue(plan.includeImages());
        assertTrue(plan.detectTables());
    }

    @Test
    void parsesVisualWidescreenControls() throws Exception {
        var plan = factory.create(objectMapper.readTree("""
            {
              "mode":"visual",
              "slideSize":"widescreen",
              "includeImages":false,
              "detectTables":false
            }
            """));

        assertEquals("visual", plan.mode());
        assertEquals("widescreen", plan.slideSize());
        assertFalse(plan.includeImages());
        assertFalse(plan.detectTables());
    }

    @Test
    void rejectsInvalidOptions() throws Exception {
        assertCode("INVALID_PDF_POWERPOINT_MODE", """
            {"mode":"perfect"}
            """);
        assertCode("INVALID_PDF_POWERPOINT_SLIDE_SIZE", """
            {"slideSize":"a4"}
            """);
        assertCode("INVALID_PDF_POWERPOINT_OPTIONS", """
            {"detectTables":"yes"}
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
