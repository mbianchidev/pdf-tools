package com.pdftools.operations.compare;

import com.pdftools.operations.OperationException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ComparePlanFactoryTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ComparePlanFactory factory = new ComparePlanFactory();

    @Test
    void suppliesBalancedDefaults() throws Exception {
        var plan = factory.create(objectMapper.readTree("{}"));

        assertEquals(120, plan.renderDpi());
        assertEquals(12, plan.pixelTolerance());
        assertEquals(2.0, plan.layoutTolerancePoints());
    }

    @Test
    void parsesComparisonControls() throws Exception {
        var plan = factory.create(objectMapper.readTree("""
            {
              "renderDpi":144,
              "pixelTolerance":4,
              "layoutTolerancePoints":0.5
            }
            """));

        assertEquals(144, plan.renderDpi());
        assertEquals(4, plan.pixelTolerance());
        assertEquals(0.5, plan.layoutTolerancePoints());
    }

    @Test
    void rejectsUnsafeControls() throws Exception {
        assertThrows(
            OperationException.class,
            () -> factory.create(objectMapper.readTree(
                "{\"renderDpi\":600}"
            ))
        );
        assertThrows(
            OperationException.class,
            () -> factory.create(objectMapper.readTree(
                "{\"pixelTolerance\":-1}"
            ))
        );
        assertThrows(
            OperationException.class,
            () -> factory.create(objectMapper.readTree(
                "{\"layoutTolerancePoints\":\"two\"}"
            ))
        );
    }
}
