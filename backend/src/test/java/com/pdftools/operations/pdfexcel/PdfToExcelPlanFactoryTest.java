package com.pdftools.operations.pdfexcel;

import com.pdftools.operations.OperationException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfToExcelPlanFactoryTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PdfToExcelPlanFactory factory =
        new PdfToExcelPlanFactory();

    @Test
    void suppliesPageSheetDefaults() throws Exception {
        var plan = factory.create(objectMapper.readTree("{}"));

        assertEquals("pages", plan.sheetMode());
        assertTrue(plan.includeNonTableText());
    }

    @Test
    void parsesTableOnlyMode() throws Exception {
        var plan = factory.create(objectMapper.readTree("""
            {"sheetMode":"tables","includeNonTableText":false}
            """));

        assertEquals("tables", plan.sheetMode());
        assertFalse(plan.includeNonTableText());
    }

    @Test
    void rejectsInvalidOptions() throws Exception {
        assertCode("INVALID_PDF_EXCEL_SHEET_MODE", """
            {"sheetMode":"document"}
            """);
        assertCode("INVALID_PDF_EXCEL_OPTIONS", """
            {"includeNonTableText":"yes"}
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
