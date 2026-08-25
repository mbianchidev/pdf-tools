package com.pdftools.operations.crop;

import com.pdftools.operations.OperationContext;
import com.pdftools.operations.OperationException;
import com.pdftools.operations.OperationInput;
import com.pdftools.operations.OperationOutput;
import com.pdftools.operations.OperationSubmission;
import com.pdftools.operations.shared.coordinates.CoordinateTransformer;
import com.pdftools.operations.shared.pages.PageExpressionParser;
import com.pdftools.operations.split.PdfSplitEngine;
import com.pdftools.operations.split.SplitPlanFactory;
import com.pdftools.operations.split.SplitProperties;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
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

class CropPdfOperationTest {

    @TempDir
    Path temporaryDirectory;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SplitProperties properties = new SplitProperties();
    private final PageExpressionParser parser = new PageExpressionParser();
    private final CropPdfOperation operation = new CropPdfOperation(
        new PdfSplitEngine(
            new SplitPlanFactory(parser, properties),
            properties
        ),
        new CropPlanFactory(parser),
        new CoordinateTransformer()
    );

    @Test
    void appliesSharedCropInVisualCoordinates() throws Exception {
        OperationOutput output = operation.execute(context(
            sourcePdf(),
            """
            {
              "crop":{"x":0.1,"y":0.2,"width":0.5,"height":0.5}
            }
            """
        )).getFirst();

        assertEquals("source_cropped.pdf", output.filename());
        try (PDDocument document = Loader.loadPDF(output.path().toFile())) {
            assertBox(document.getPage(0).getCropBox(), 30, 50, 100, 50);
            assertBox(document.getPage(1).getCropBox(), 40, 10, 100, 50);
        }
    }

    @Test
    void appliesDistinctPerPageCropsAndLeavesOthersUnchanged()
            throws Exception {
        OperationOutput output = operation.execute(context(
            sourcePdf(),
            """
            {
              "crops":[
                {
                  "pages":"1",
                  "rectangle":{"x":0,"y":0,"width":0.5,"height":1}
                },
                {
                  "pages":"2",
                  "rectangle":{"x":0.25,"y":0.25,"width":0.5,"height":0.5}
                }
              ]
            }
            """
        )).getFirst();

        try (PDDocument document = Loader.loadPDF(output.path().toFile())) {
            assertBox(document.getPage(0).getCropBox(), 10, 20, 100, 100);
            assertBox(document.getPage(1).getCropBox(), 50, 25, 100, 50);
            assertBox(document.getPage(2).getCropBox(), 0, 0, 300, 100);
        }
    }

    @Test
    void rejectsInvalidRectanglesAndOverlappingCrops() throws Exception {
        assertCode(
            "INVALID_CROP_RECTANGLE",
            """
            {"crop":{"x":0.8,"y":0,"width":0.3,"height":1}}
            """
        );
        assertCode(
            "OVERLAPPING_CROPS",
            """
            {"crops":[
              {
                "pages":"1-2",
                "rectangle":{"x":0,"y":0,"width":0.5,"height":1}
              },
              {
                "pages":"2",
                "rectangle":{"x":0.5,"y":0,"width":0.5,"height":1}
              }
            ]}
            """
        );
    }

    @Test
    void validatesSubmissionAndCreatesDeterministicOutput() throws Exception {
        OperationSubmission.UploadDescriptor pdf =
            new OperationSubmission.UploadDescriptor(
                1,
                "source.pdf",
                "application/pdf",
                100
            );
        assertEquals(
            "CROP_REQUIRED",
            assertThrows(
                OperationException.class,
                () -> operation.validateSubmission(new OperationSubmission(
                    objectMapper.readTree("{}"),
                    List.of(pdf)
                ))
            ).getCode()
        );

        Path source = sourcePdf();
        String options =
            "{\"crop\":{\"x\":0.1,\"y\":0.1,\"width\":0.8,\"height\":0.8}}";
        OperationOutput first = operation.execute(context(source, options))
            .getFirst();
        OperationOutput second = operation.execute(context(source, options))
            .getFirst();
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

    private void assertBox(
            PDRectangle box,
            float x,
            float y,
            float width,
            float height) {
        assertEquals(x, box.getLowerLeftX(), 0.001);
        assertEquals(y, box.getLowerLeftY(), 0.001);
        assertEquals(width, box.getWidth(), 0.001);
        assertEquals(height, box.getHeight(), 0.001);
    }

    private Path sourcePdf() throws Exception {
        Path path = temporaryDirectory.resolve(
            "source-" + UUID.randomUUID() + ".pdf"
        );
        try (PDDocument document = new PDDocument()) {
            PDPage first = new PDPage(new PDRectangle(10, 20, 200, 100));
            first.setCropBox(new PDRectangle(10, 20, 200, 100));
            PDPage second = new PDPage(new PDRectangle(200, 100));
            second.setRotation(90);
            PDPage third = new PDPage(new PDRectangle(300, 100));
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
            Files.createTempDirectory(temporaryDirectory, "crop-context-"),
            ignored -> {
            },
            () -> false
        );
    }
}
