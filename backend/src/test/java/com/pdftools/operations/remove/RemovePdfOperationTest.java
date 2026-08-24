package com.pdftools.operations.remove;

import com.pdftools.operations.OperationContext;
import com.pdftools.operations.OperationException;
import com.pdftools.operations.OperationInput;
import com.pdftools.operations.OperationOutput;
import com.pdftools.operations.OperationSubmission;
import com.pdftools.operations.shared.pages.PageExpressionParser;
import com.pdftools.operations.split.PdfSplitEngine;
import com.pdftools.operations.split.SplitPlanFactory;
import com.pdftools.operations.split.SplitProperties;
import com.pdftools.testing.PdfTestFixtures;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

import java.awt.Color;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RemovePdfOperationTest {

    @TempDir
    Path temporaryDirectory;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SplitProperties properties = new SplitProperties();
    private final PageExpressionParser parser = new PageExpressionParser();
    private final RemovePdfOperation operation = new RemovePdfOperation(
        new PdfSplitEngine(
            new SplitPlanFactory(parser, properties),
            properties
        ),
        new RemovePagePlanner(parser)
    );

    @Test
    void removesRangesAndPreservesRemainingPageOrder() throws Exception {
        Path source = sourcePdf();

        OperationOutput output = operation.execute(context(
            source,
            "{\"pages\":\"2,4-5\"}"
        )).getFirst();

        assertEquals("source_pages_removed.pdf", output.filename());
        try (PDDocument document = Loader.loadPDF(output.path().toFile())) {
            assertEquals(2, document.getNumberOfPages());
            assertEquals(101, document.getPage(0).getMediaBox().getWidth());
            assertEquals(103, document.getPage(1).getMediaBox().getWidth());
        }
    }

    @Test
    void rejectsDuplicateInvalidAndAllPageRemoval() throws Exception {
        assertCode("DUPLICATE_PAGE", "2,2");
        assertCode("PAGE_OUT_OF_RANGE", "6");
        assertCode("DESCENDING_PAGE_RANGE", "4-2");
        assertCode("CANNOT_REMOVE_ALL_PAGES", "all");
    }

    @Test
    void validatesSubmissionContract() throws Exception {
        OperationSubmission.UploadDescriptor pdf =
            new OperationSubmission.UploadDescriptor(
                1,
                "source.pdf",
                "application/pdf",
                100
            );
        assertEquals(
            "REMOVE_PAGES_REQUIRED",
            assertThrows(
                OperationException.class,
                () -> operation.validateSubmission(new OperationSubmission(
                    objectMapper.readTree("{}"),
                    List.of(pdf)
                ))
            ).getCode()
        );
        assertEquals(
            "INVALID_REMOVE_PAGES",
            assertThrows(
                OperationException.class,
                () -> operation.validateSubmission(new OperationSubmission(
                    objectMapper.readTree("{\"pages\":[1,2]}"),
                    List.of(pdf)
                ))
            ).getCode()
        );
    }

    @Test
    void createsByteDeterministicOutputs() throws Exception {
        Path source = sourcePdf();

        OperationOutput first = operation.execute(context(
            source,
            "{\"pages\":\"2-4\"}"
        )).getFirst();
        OperationOutput second = operation.execute(context(
            source,
            "{\"pages\":\"2-4\"}"
        )).getFirst();

        assertArrayEquals(
            Files.readAllBytes(first.path()),
            Files.readAllBytes(second.path())
        );
    }

    private void assertCode(String expectedCode, String expression)
            throws Exception {
        OperationException exception = assertThrows(
            OperationException.class,
            () -> operation.execute(context(
                sourcePdf(),
                "{\"pages\":\"" + expression + "\"}"
            ))
        );
        assertEquals(expectedCode, exception.getCode());
    }

    private Path sourcePdf() throws Exception {
        return PdfTestFixtures.coloredPdf(
            temporaryDirectory.resolve("source-" + UUID.randomUUID() + ".pdf"),
            List.of(
                new PdfTestFixtures.PageSpec(101, 100, Color.RED),
                new PdfTestFixtures.PageSpec(102, 100, Color.GREEN),
                new PdfTestFixtures.PageSpec(103, 100, Color.BLUE),
                new PdfTestFixtures.PageSpec(104, 100, Color.YELLOW),
                new PdfTestFixtures.PageSpec(105, 100, Color.MAGENTA)
            )
        );
    }

    private OperationContext context(Path source, String options)
            throws Exception {
        Path workspace = Files.createTempDirectory(
            temporaryDirectory,
            "remove-context-"
        );
        return new OperationContext(
            UUID.randomUUID(),
            objectMapper.readTree(options),
            List.of(new OperationInput(
                1,
                source,
                "source.pdf",
                "application/pdf",
                Files.size(source),
                "test-sha"
            )),
            workspace,
            ignored -> {
            },
            () -> false
        );
    }
}
