package com.pdftools.operations.split;

import com.pdftools.operations.OperationException;
import com.pdftools.operations.shared.pages.PageExpressionParser;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SplitPlanFactoryTest {

    private final SplitProperties properties = new SplitProperties();
    private final SplitPlanFactory factory = new SplitPlanFactory(
        new PageExpressionParser(),
        properties
    );
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void createsIndividualAndFixedPlans() throws Exception {
        assertEquals(
            List.of(List.of(1), List.of(2), List.of(3)),
            pages(factory.create(objectMapper.readTree("{\"mode\":\"individual\"}"), 3))
        );
        assertEquals(
            List.of(List.of(1, 2), List.of(3, 4), List.of(5)),
            pages(factory.create(
                objectMapper.readTree("{\"mode\":\"fixed\",\"fixedGroupSize\":2}"),
                5
            ))
        );
    }

    @Test
    void preservesRangeGroupAndPageOrder() throws Exception {
        List<SplitGroup> groups = factory.create(
            objectMapper.readTree(
                "{\"mode\":\"ranges\",\"ranges\":[\"1-2\",\"4,3\"]}"
            ),
            4
        );

        assertEquals(List.of(List.of(1, 2), List.of(4, 3)), pages(groups));
    }

    @Test
    void rejectsOverlappingRangesAndExcessOutputs() throws Exception {
        OperationException overlap = assertThrows(
            OperationException.class,
            () -> factory.create(
                objectMapper.readTree(
                    "{\"mode\":\"ranges\",\"ranges\":[\"1-2\",\"2-3\"]}"
                ),
                3
            )
        );
        assertEquals("OVERLAPPING_SPLIT_RANGES", overlap.getCode());

        properties.setMaxOutputs(2);
        OperationException outputs = assertThrows(
            OperationException.class,
            () -> factory.create(
                objectMapper.readTree("{\"mode\":\"individual\"}"),
                3
            )
        );
        assertEquals("SPLIT_OUTPUT_LIMIT_EXCEEDED", outputs.getCode());
    }

    @Test
    void rejectsInvalidFixedSizesAndPageLimits() throws Exception {
        assertEquals(
            "INVALID_FIXED_GROUP_SIZE",
            assertThrows(
                OperationException.class,
                () -> factory.create(
                    objectMapper.readTree(
                        "{\"mode\":\"fixed\",\"fixedGroupSize\":0}"
                    ),
                    3
                )
            ).getCode()
        );

        properties.setMaxPages(2);
        assertEquals(
            "PDF_PAGE_LIMIT_EXCEEDED",
            assertThrows(
                OperationException.class,
                () -> factory.create(
                    objectMapper.readTree("{\"mode\":\"individual\"}"),
                    3
                )
            ).getCode()
        );
    }

    private List<List<Integer>> pages(List<SplitGroup> groups) {
        return groups.stream().map(SplitGroup::pages).toList();
    }
}
