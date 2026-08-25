package com.pdftools.operations.pdfa;

import com.pdftools.operations.OperationException;
import org.junit.jupiter.api.Test;
import org.verapdf.pdfa.flavours.PDFAFlavour;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PdfAPlanFactoryTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PdfAPlanFactory factory = new PdfAPlanFactory();

    @Test
    void defaultsToPdfA2b() throws Exception {
        var plan = factory.create(objectMapper.readTree("{}"));

        assertEquals("pdfa-2b", plan.profile().option());
        assertEquals(2, plan.profile().libreOfficeVersion());
        assertEquals(PDFAFlavour.PDFA_2_B, plan.profile().flavour());
    }

    @Test
    void acceptsSelectedBLevelProfiles() throws Exception {
        assertEquals(
            PDFAFlavour.PDFA_1_B,
            factory.create(objectMapper.readTree(
                "{\"profile\":\"pdfa-1b\"}"
            )).profile().flavour()
        );
        assertEquals(
            PDFAFlavour.PDFA_3_B,
            factory.create(objectMapper.readTree(
                "{\"profile\":\"PDFA-3B\"}"
            )).profile().flavour()
        );
    }

    @Test
    void rejectsUnsupportedOrMistypedProfiles() throws Exception {
        assertThrows(
            OperationException.class,
            () -> factory.create(objectMapper.readTree(
                "{\"profile\":\"pdfa-2a\"}"
            ))
        );
        assertThrows(
            OperationException.class,
            () -> factory.create(objectMapper.readTree(
                "{\"profile\":2}"
            ))
        );
    }
}
