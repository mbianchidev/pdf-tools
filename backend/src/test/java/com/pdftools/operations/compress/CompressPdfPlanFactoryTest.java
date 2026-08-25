package com.pdftools.operations.compress;

import com.pdftools.operations.OperationException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CompressPdfPlanFactoryTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CompressPdfPlanFactory factory =
        new CompressPdfPlanFactory();

    @Test
    void defaultsToRecommendedCompression() throws Exception {
        assertEquals(
            CompressPdfPlanFactory.CompressionMode.RECOMMENDED,
            factory.create(objectMapper.readTree("{}")).mode()
        );
    }

    @Test
    void acceptsEveryCompressionMode() throws Exception {
        assertEquals(
            CompressPdfPlanFactory.CompressionMode.LOW,
            factory.create(objectMapper.readTree(
                "{\"mode\":\"low\"}"
            )).mode()
        );
        assertEquals(
            CompressPdfPlanFactory.CompressionMode.EXTREME,
            factory.create(objectMapper.readTree(
                "{\"mode\":\"EXTREME\"}"
            )).mode()
        );
    }

    @Test
    void rejectsUnknownOrMistypedModes() throws Exception {
        assertThrows(
            OperationException.class,
            () -> factory.create(objectMapper.readTree(
                "{\"mode\":\"maximum\"}"
            ))
        );
        assertThrows(
            OperationException.class,
            () -> factory.create(objectMapper.readTree(
                "{\"mode\":1}"
            ))
        );
    }
}
