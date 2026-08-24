package com.pdftools.operations.rotate;

import com.pdftools.operations.OperationContext;
import com.pdftools.operations.OperationException;
import com.pdftools.operations.OperationInput;
import com.pdftools.operations.OperationOutput;
import com.pdftools.operations.OperationSubmission;
import com.pdftools.operations.shared.pages.PageExpressionParser;
import com.pdftools.operations.split.PdfSplitEngine;
import com.pdftools.operations.split.SplitPlanFactory;
import com.pdftools.operations.split.SplitProperties;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RotatePdfOperationTest {

    @TempDir
    Path temporaryDirectory;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SplitProperties properties = new SplitProperties();
    private final PageExpressionParser parser = new PageExpressionParser();
    private final RotatePdfOperation operation = new RotatePdfOperation(
        new PdfSplitEngine(
            new SplitPlanFactory(parser, properties),
            properties
        ),
        new RotatePlanFactory(parser)
    );

    @Test
    void rotatesTheWholeDocumentRelativeToExistingRotation() throws Exception {
        OperationOutput output = operation.execute(context(
            sourcePdf(),
            "{\"rotation\":90}"
        )).getFirst();

        assertEquals("source_rotated.pdf", output.filename());
        try (PDDocument document = Loader.loadPDF(output.path().toFile())) {
            assertEquals(90, document.getPage(0).getRotation());
            assertEquals(180, document.getPage(1).getRotation());
            assertEquals(0, document.getPage(2).getRotation());
        }
    }

    @Test
    void rotatesOnlySelectedPages() throws Exception {
        OperationOutput output = operation.execute(context(
            sourcePdf(),
            "{\"rotation\":270,\"pages\":\"1,3\"}"
        )).getFirst();

        try (PDDocument document = Loader.loadPDF(output.path().toFile())) {
            assertEquals(270, document.getPage(0).getRotation());
            assertEquals(90, document.getPage(1).getRotation());
            assertEquals(180, document.getPage(2).getRotation());
        }
    }

    @Test
    void appliesDistinctPerPageRotations() throws Exception {
        OperationOutput output = operation.execute(context(
            sourcePdf(),
            """
            {
              "rotations": [
                {"pages": "1", "rotation": 90},
                {"pages": "2-3", "rotation": 180}
              ]
            }
            """
        )).getFirst();

        try (PDDocument document = Loader.loadPDF(output.path().toFile())) {
            assertEquals(90, document.getPage(0).getRotation());
            assertEquals(270, document.getPage(1).getRotation());
            assertEquals(90, document.getPage(2).getRotation());
        }
    }

    @Test
    void rejectsInvalidAnglesAndDuplicatePages() throws Exception {
        assertCode("INVALID_ROTATION", "{\"rotation\":45}");
        assertCode(
            "INVALID_ROTATION",
            "{\"rotation\":\"90\"}"
        );
        assertCode(
            "DUPLICATE_PAGE",
            "{\"rotation\":90,\"pages\":\"1,1\"}"
        );
        assertCode(
            "OVERLAPPING_ROTATIONS",
            """
            {"rotations":[
              {"pages":"1-2","rotation":90},
              {"pages":"2","rotation":180}
            ]}
            """
        );
        assertCode(
            "INVALID_ROTATION_OPTIONS",
            """
            {
              "pages":"999",
              "rotations":[{"pages":"1","rotation":90}]
            }
            """
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
            "ROTATION_REQUIRED",
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

        OperationOutput first = operation.execute(context(
            source,
            "{\"rotation\":180,\"pages\":\"2\"}"
        )).getFirst();
        OperationOutput second = operation.execute(context(
            source,
            "{\"rotation\":180,\"pages\":\"2\"}"
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
        Path path = temporaryDirectory.resolve(
            "source-" + UUID.randomUUID() + ".pdf"
        );
        try (PDDocument document = new PDDocument()) {
            PDPage first = new PDPage();
            PDPage second = new PDPage();
            PDPage third = new PDPage();
            second.setRotation(90);
            third.setRotation(270);
            document.addPage(first);
            document.addPage(second);
            document.addPage(third);
            document.save(path.toFile());
        }
        return path;
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
            Files.createTempDirectory(temporaryDirectory, "rotate-context-"),
            ignored -> {
            },
            () -> false
        );
    }
}
