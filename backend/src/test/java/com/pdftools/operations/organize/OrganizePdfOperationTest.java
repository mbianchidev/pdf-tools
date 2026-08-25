package com.pdftools.operations.organize;

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

class OrganizePdfOperationTest {

    @TempDir
    Path temporaryDirectory;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SplitProperties properties = new SplitProperties();
    private final PageExpressionParser parser = new PageExpressionParser();
    private final OrganizePdfOperation operation = new OrganizePdfOperation(
        new PdfSplitEngine(
            new SplitPlanFactory(parser, properties),
            properties
        ),
        new OrganizePlanFactory(properties)
    );

    @Test
    void reordersRotatesDuplicatesAndDeletesPages() throws Exception {
        OperationOutput output = operation.execute(context(
            sourcePdf(),
            """
            {"pages":[
              {"page":3,"rotation":90},
              {"page":1,"rotation":0},
              {"page":1,"rotation":180}
            ]}
            """
        )).getFirst();

        assertEquals("source_organized.pdf", output.filename());
        try (PDDocument document = Loader.loadPDF(output.path().toFile())) {
            assertEquals(3, document.getNumberOfPages());
            assertEquals(103, document.getPage(0).getMediaBox().getWidth());
            assertEquals(101, document.getPage(1).getMediaBox().getWidth());
            assertEquals(101, document.getPage(2).getMediaBox().getWidth());
            assertEquals(90, document.getPage(0).getRotation());
            assertEquals(0, document.getPage(1).getRotation());
            assertEquals(180, document.getPage(2).getRotation());
        }
    }

    @Test
    void rejectsEmptyInvalidAndOutOfRangePlans() throws Exception {
        assertCode("ORGANIZE_PAGES_REQUIRED", "{\"pages\":[]}");
        assertCode(
            "INVALID_ORGANIZE_PAGE",
            "{\"pages\":[{\"page\":1,\"rotation\":45}]}"
        );
        assertCode(
            "PAGE_OUT_OF_RANGE",
            "{\"pages\":[{\"page\":4,\"rotation\":0}]}"
        );
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
            "ORGANIZE_PAGES_REQUIRED",
            assertThrows(
                OperationException.class,
                () -> operation.validateSubmission(new OperationSubmission(
                    objectMapper.readTree("{}"),
                    List.of(pdf)
                ))
            ).getCode()
        );
    }

    @Test
    void createsByteDeterministicOutputs() throws Exception {
        Path source = sourcePdf();
        String options = """
            {"pages":[
              {"page":2,"rotation":270},
              {"page":1,"rotation":0}
            ]}
            """;

        OperationOutput first = operation.execute(context(
            source,
            options
        )).getFirst();
        OperationOutput second = operation.execute(context(
            source,
            options
        )).getFirst();

        assertArrayEquals(
            Files.readAllBytes(first.path()),
            Files.readAllBytes(second.path())
        );
    }

    private void assertCode(String code, String options) throws Exception {
        OperationException exception = assertThrows(
            OperationException.class,
            () -> operation.execute(context(sourcePdf(), options))
        );
        assertEquals(code, exception.getCode());
    }

    private Path sourcePdf() throws Exception {
        return PdfTestFixtures.coloredPdf(
            temporaryDirectory.resolve("source-" + UUID.randomUUID() + ".pdf"),
            List.of(
                new PdfTestFixtures.PageSpec(101, 100, Color.RED),
                new PdfTestFixtures.PageSpec(102, 100, Color.GREEN),
                new PdfTestFixtures.PageSpec(103, 100, Color.BLUE)
            )
        );
    }

    private OperationContext context(Path source, String options)
            throws Exception {
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
            Files.createTempDirectory(temporaryDirectory, "organize-context-"),
            ignored -> {
            },
            () -> false
        );
    }
}
