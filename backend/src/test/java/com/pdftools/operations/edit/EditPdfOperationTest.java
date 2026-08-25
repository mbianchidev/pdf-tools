package com.pdftools.operations.edit;

import com.pdftools.operations.OperationContext;
import com.pdftools.operations.OperationException;
import com.pdftools.operations.OperationInput;
import com.pdftools.operations.OperationOutput;
import com.pdftools.operations.OperationSubmission;
import com.pdftools.operations.shared.image.JpegPdfImageFactory;
import com.pdftools.operations.shared.image.JpegValidationProperties;
import com.pdftools.operations.shared.image.JpegValidationService;
import com.pdftools.operations.shared.pages.PageExpressionParser;
import com.pdftools.operations.split.PdfSplitEngine;
import com.pdftools.operations.split.SplitPlanFactory;
import com.pdftools.operations.split.SplitProperties;
import com.pdftools.operations.watermark.WatermarkImagePreparer;
import com.pdftools.operations.watermark.WatermarkProperties;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationHighlight;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationText;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EditPdfOperationTest {

    @TempDir
    Path temporaryDirectory;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SplitProperties splitProperties = new SplitProperties();
    private final EditProperties properties = new EditProperties();
    private final JpegValidationProperties validationProperties =
        new JpegValidationProperties();
    private final EditPdfOperation operation = new EditPdfOperation(
        new PdfSplitEngine(
            new SplitPlanFactory(
                new PageExpressionParser(),
                splitProperties
            ),
            splitProperties
        ),
        new EditPlanFactory(properties),
        properties,
        new WatermarkImagePreparer(
            new WatermarkProperties(),
            new JpegValidationService(validationProperties),
            validationProperties,
            new JpegPdfImageFactory()
        ),
        new EditRenderer()
    );

    @Test
    void appliesTextImagesShapesAndAnnotationsInOnePlan()
            throws Exception {
        Path image = png(Color.BLUE);
        OperationOutput output = operation.execute(context(
            List.of(sourcePdf(), image),
            List.of("source.pdf", "image.png"),
            List.of("application/pdf", "image/png"),
            """
            {
              "elements":[
                {
                  "type":"text","page":1,"x":0.5,"y":0.2,
                  "text":"EDITED","font":"helvetica-bold",
                  "fontSize":24,"color":"#ff0000","opacity":1
                },
                {
                  "type":"rectangle","page":1,
                  "x":0.1,"y":0.45,"width":0.25,"height":0.2,
                  "strokeColor":"#00aa00","fillColor":"#ccffcc",
                  "strokeWidth":2,"opacity":0.8
                },
                {
                  "type":"highlight","page":1,
                  "x":0.4,"y":0.65,"width":0.4,"height":0.1,
                  "color":"#fff176","opacity":0.35
                },
                {
                  "type":"image","page":2,"x":0.5,"y":0.5,
                  "width":0.4,"imageIndex":0,"opacity":1
                },
                {
                  "type":"ellipse","page":2,
                  "x":0.05,"y":0.1,"width":0.2,"height":0.2,
                  "strokeColor":"#4f46e5","fillColor":"none"
                },
                {
                  "type":"line","page":2,
                  "x":0.1,"y":0.9,"x2":0.9,"y2":0.9,
                  "color":"#111111","strokeWidth":2
                },
                {
                  "type":"note","page":2,"x":0.8,"y":0.2,
                  "contents":"Review this page","title":"Reviewer",
                  "color":"#ffb300"
                }
              ]
            }
            """
        )).getFirst();

        assertEquals("source_edited.pdf", output.filename());
        try (PDDocument document = Loader.loadPDF(output.path().toFile())) {
            assertEquals(2, document.getNumberOfPages());
            assertInstanceOf(
                PDAnnotationHighlight.class,
                document.getPage(0).getAnnotations().getFirst()
            );
            assertInstanceOf(
                PDAnnotationText.class,
                document.getPage(1).getAnnotations().getFirst()
            );
            BufferedImage second = new PDFRenderer(document).renderImage(
                1,
                2
            );
            Color center = new Color(second.getRGB(
                second.getWidth() / 2,
                second.getHeight() / 2
            ));
            assertTrue(center.getBlue() > 220);
            assertTrue(center.getRed() < 30);
        }
    }

    @Test
    void rejectsInvalidPagesImageReferencesAndEmptyPlans()
            throws Exception {
        OperationSubmission.UploadDescriptor pdf = descriptor(
            1,
            "source.pdf",
            "application/pdf"
        );
        assertSubmissionCode(
            "EDIT_ELEMENTS_REQUIRED",
            """
            {"elements":[]}
            """,
            List.of(pdf)
        );
        assertSubmissionCode(
            "INVALID_EDIT_IMAGE_INDEX",
            """
            {
              "elements":[
                {"type":"image","page":1,"imageIndex":0}
              ]
            }
            """,
            List.of(pdf)
        );
        assertCode(
            "EDIT_PAGE_OUT_OF_RANGE",
            """
            {
              "elements":[
                {"type":"text","page":3,"text":"outside"}
              ]
            }
            """
        );
    }

    @Test
    void clampsEdgeHighlightsAndPreservesRotatedAnnotationGeometry()
            throws Exception {
        OperationOutput output = operation.execute(context(
            List.of(rotatedPdf()),
            List.of("rotated.pdf"),
            List.of("application/pdf"),
            """
            {
              "elements":[
                {
                  "type":"highlight","page":1,
                  "x":0.9,"y":0.95,"width":0.4,"height":0.1
                },
                {
                  "type":"note","page":1,
                  "x":0.8,"y":0.2,"contents":"Rotated note"
                }
              ]
            }
            """
        )).getFirst();

        try (PDDocument document = Loader.loadPDF(output.path().toFile())) {
            PDAnnotationHighlight highlight = assertInstanceOf(
                PDAnnotationHighlight.class,
                document.getPage(0).getAnnotations().get(0)
            );
            float[] quads = highlight.getQuadPoints();
            assertEquals(180, quads[0], 0.01);
            assertEquals(60, quads[1], 0.01);
            assertEquals(180, quads[2], 0.01);
            assertEquals(100, quads[3], 0.01);
            PDAnnotationText note = assertInstanceOf(
                PDAnnotationText.class,
                document.getPage(0).getAnnotations().get(1)
            );
            PDRectangle noteBounds = note.getRectangle();
            assertEquals(35.5, noteBounds.getLowerLeftX(), 0.01);
            assertEquals(75, noteBounds.getLowerLeftY(), 0.01);
            assertEquals(9, noteBounds.getWidth(), 0.01);
            assertEquals(10, noteBounds.getHeight(), 0.01);
        }
    }

    @Test
    void validatesPagesBeforeLazyImageDecodeAndEnforcesBudget()
            throws Exception {
        Path invalidImage = temporaryDirectory.resolve("invalid.png");
        Files.writeString(invalidImage, "not an image");
        OperationException pageFailure = assertThrows(
            OperationException.class,
            () -> operation.execute(context(
                List.of(sourcePdf(), invalidImage),
                List.of("source.pdf", "invalid.png"),
                List.of("application/pdf", "image/png"),
                """
                {
                  "elements":[
                    {
                      "type":"image","page":3,
                      "imageIndex":0,"x":0.5,"y":0.5
                    }
                  ]
                }
                """
            ))
        );
        assertEquals("EDIT_PAGE_OUT_OF_RANGE", pageFailure.getCode());

        properties.setMaxTotalDecodedImageBytes(1);
        OperationException budgetFailure = assertThrows(
            OperationException.class,
            () -> operation.execute(context(
                List.of(sourcePdf(), png(Color.BLUE)),
                List.of("source.pdf", "image.png"),
                List.of("application/pdf", "image/png"),
                """
                {
                  "elements":[
                    {
                      "type":"image","page":1,
                      "imageIndex":0,"x":0.5,"y":0.5
                    }
                  ]
                }
                """
            ))
        );
        assertEquals(
            "EDIT_DECODED_IMAGE_LIMIT_EXCEEDED",
            budgetFailure.getCode()
        );
    }

    private void assertSubmissionCode(
            String code,
            String options,
            List<OperationSubmission.UploadDescriptor> files)
            throws Exception {
        OperationException exception = assertThrows(
            OperationException.class,
            () -> operation.validateSubmission(new OperationSubmission(
                objectMapper.readTree(options),
                files
            ))
        );
        assertEquals(code, exception.getCode());
    }

    private void assertCode(String code, String options)
            throws Exception {
        OperationException exception = assertThrows(
            OperationException.class,
            () -> operation.execute(context(
                List.of(sourcePdf()),
                List.of("source.pdf"),
                List.of("application/pdf"),
                options
            ))
        );
        assertEquals(code, exception.getCode());
    }

    private OperationSubmission.UploadDescriptor descriptor(
            int position,
            String name,
            String mediaType) {
        return new OperationSubmission.UploadDescriptor(
            position,
            name,
            mediaType,
            100
        );
    }

    private Path sourcePdf() throws Exception {
        Path source = temporaryDirectory.resolve(
            "source-" + UUID.randomUUID() + ".pdf"
        );
        try (PDDocument document = new PDDocument()) {
            addWhitePage(document);
            addWhitePage(document);
            document.save(source.toFile());
        }
        return source;
    }

    private Path rotatedPdf() throws Exception {
        Path source = temporaryDirectory.resolve("rotated.pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(new PDRectangle(200, 100));
            page.setRotation(90);
            page.setUserUnit(2);
            document.addPage(page);
            document.save(source.toFile());
        }
        return source;
    }

    private void addWhitePage(PDDocument document) throws Exception {
        PDPage page = new PDPage(new PDRectangle(200, 100));
        document.addPage(page);
        try (PDPageContentStream content =
                new PDPageContentStream(document, page)) {
            content.setNonStrokingColor(Color.WHITE);
            content.addRect(0, 0, 200, 100);
            content.fill();
        }
    }

    private Path png(Color color) throws Exception {
        BufferedImage image = new BufferedImage(
            40,
            20,
            BufferedImage.TYPE_INT_RGB
        );
        java.awt.Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(color);
            graphics.fillRect(0, 0, 40, 20);
        } finally {
            graphics.dispose();
        }
        Path path = temporaryDirectory.resolve("image.png");
        ImageIO.write(image, "png", path.toFile());
        image.flush();
        return path;
    }

    private OperationContext context(
            List<Path> paths,
            List<String> names,
            List<String> mediaTypes,
            String options) throws Exception {
        List<OperationInput> inputs = new ArrayList<>();
        for (int index = 0; index < paths.size(); index++) {
            inputs.add(new OperationInput(
                index + 1,
                paths.get(index),
                names.get(index),
                mediaTypes.get(index),
                Files.size(paths.get(index)),
                "sha-" + index
            ));
        }
        return new OperationContext(
            UUID.randomUUID(),
            objectMapper.readTree(options),
            inputs,
            Files.createTempDirectory(
                temporaryDirectory,
                "edit-context-"
            ),
            ignored -> {
            },
            () -> false
        );
    }
}
