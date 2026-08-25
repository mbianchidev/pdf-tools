package com.pdftools.operations.pdfppt;

import com.pdftools.operations.OperationContext;
import com.pdftools.operations.OperationException;
import com.pdftools.operations.OperationInput;
import com.pdftools.operations.OperationOutput;
import com.pdftools.operations.OperationSubmission;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFPictureShape;
import org.apache.poi.xslf.usermodel.XSLFTable;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfToPowerPointOperationTest {

    @TempDir
    Path temporaryDirectory;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PdfToPowerPointProperties properties =
        new PdfToPowerPointProperties();
    private final PdfToPowerPointPlanFactory planFactory =
        new PdfToPowerPointPlanFactory();
    private final PdfToPowerPointOperation operation =
        new PdfToPowerPointOperation(
            new PdfToPowerPointEngine(properties),
            planFactory,
            properties
        );

    @Test
    void createsEditableSlidesWithTextTableAndImage() throws Exception {
        OperationOutput output = operation.execute(context(
            sourcePdf(),
            """
            {
              "mode":"editable",
              "slideSize":"source",
              "includeImages":true,
              "detectTables":true
            }
            """
        )).getFirst();

        assertEquals("report.pptx", output.filename());
        try (XMLSlideShow presentation = new XMLSlideShow(
                Files.newInputStream(output.path()))) {
            assertEquals(2, presentation.getSlides().size());
            assertEquals(612, presentation.getPageSize().width);
            assertEquals(792, presentation.getPageSize().height);
            var first = presentation.getSlides().getFirst();
            assertTrue(first.getShapes().stream()
                .filter(XSLFTextShape.class::isInstance)
                .map(XSLFTextShape.class::cast)
                .anyMatch(shape -> shape.getText()
                    .contains("Quarterly Report")));
            assertTrue(first.getShapes().stream()
                .anyMatch(XSLFTable.class::isInstance));
            assertTrue(first.getShapes().stream()
                .anyMatch(XSLFPictureShape.class::isInstance));
            assertTrue(presentation.getSlides().get(1).getShapes().stream()
                .filter(XSLFTextShape.class::isInstance)
                .map(XSLFTextShape.class::cast)
                .anyMatch(shape -> shape.getText()
                    .contains("SECOND SLIDE")));
        }
    }

    @Test
    void createsOneVisualPicturePerSlide() throws Exception {
        OperationOutput output = operation.execute(context(
            sourcePdf(),
            """
            {"mode":"visual","slideSize":"widescreen"}
            """
        )).getFirst();

        try (XMLSlideShow presentation = new XMLSlideShow(
                Files.newInputStream(output.path()))) {
            assertEquals(2, presentation.getSlides().size());
            assertEquals(960, presentation.getPageSize().width);
            assertEquals(540, presentation.getPageSize().height);
            assertTrue(presentation.getSlides().stream().allMatch(
                slide -> slide.getShapes().stream()
                    .filter(XSLFPictureShape.class::isInstance)
                    .count() == 1
            ));
        }
    }

    @Test
    void validatesSubmissionAndRejectsEncryptedPdf() throws Exception {
        operation.validateSubmission(new OperationSubmission(
            objectMapper.readTree("{}"),
            List.of(descriptor("report.pdf", "application/pdf", 100))
        ));
        OperationException invalid = assertThrows(
            OperationException.class,
            () -> operation.validateSubmission(new OperationSubmission(
                objectMapper.readTree("{}"),
                List.of(descriptor("report.txt", "text/plain", 100))
            ))
        );
        assertEquals("INVALID_PDF_FILE", invalid.getCode());

        Path encrypted = temporaryDirectory.resolve("encrypted.pdf");
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            document.protect(
                new org.apache.pdfbox.pdmodel.encryption
                    .StandardProtectionPolicy(
                        "owner",
                        "user",
                        new org.apache.pdfbox.pdmodel.encryption
                            .AccessPermission()
                    )
            );
            document.save(encrypted.toFile());
        }
        OperationException failure = assertThrows(
            OperationException.class,
            () -> operation.execute(context(encrypted, "{}"))
        );
        assertEquals("ENCRYPTED_PDF_NOT_SUPPORTED", failure.getCode());
    }

    @Test
    void enforcesPageAndImageBudgets() throws Exception {
        PdfToPowerPointProperties pageProperties =
            new PdfToPowerPointProperties();
        pageProperties.setMaxPages(1);
        assertEngineCode(
            "PDF_PAGE_LIMIT_EXCEEDED",
            pageProperties,
            "{\"mode\":\"editable\"}"
        );

        PdfToPowerPointProperties imageProperties =
            new PdfToPowerPointProperties();
        imageProperties.setMaxImages(1);
        assertEngineCode(
            "PDF_POWERPOINT_IMAGE_LIMIT_EXCEEDED",
            imageProperties,
            "{\"mode\":\"visual\"}"
        );

        PdfToPowerPointProperties textProperties =
            new PdfToPowerPointProperties();
        textProperties.setMaxTextCharacters(5);
        assertEngineCode(
            "PDF_POWERPOINT_TEXT_LIMIT_EXCEEDED",
            textProperties,
            "{\"mode\":\"editable\"}"
        );
    }

    @Test
    void fallsBackToVisualSlideForRotatedPage() throws Exception {
        OperationOutput output = operation.execute(context(
            rotatedPdf(),
            "{\"mode\":\"editable\",\"includeImages\":false}"
        )).getFirst();

        try (XMLSlideShow presentation = new XMLSlideShow(
                Files.newInputStream(output.path()))) {
            var shapes = presentation.getSlides().getFirst().getShapes();
            assertEquals(
                1,
                shapes.stream()
                    .filter(XSLFPictureShape.class::isInstance)
                    .count()
            );
            assertFalse(shapes.stream()
                .anyMatch(XSLFTextShape.class::isInstance));
        }
    }

    @Test
    void downsamplesVisualSlidesToPixelBudget() throws Exception {
        PdfToPowerPointProperties configured =
            new PdfToPowerPointProperties();
        configured.setMaxRenderPixelsPerPage(100_000);
        configured.setMaxPixelsPerImage(100_000);
        PdfToPowerPointOperation configuredOperation =
            new PdfToPowerPointOperation(
                new PdfToPowerPointEngine(configured),
                planFactory,
                configured
            );

        OperationOutput output = configuredOperation.execute(context(
            sourcePdf(),
            "{\"mode\":\"visual\"}"
        )).getFirst();

        try (XMLSlideShow presentation = new XMLSlideShow(
                Files.newInputStream(output.path()))) {
            XSLFPictureShape picture = presentation.getSlides().getFirst()
                .getShapes().stream()
                .filter(XSLFPictureShape.class::isInstance)
                .map(XSLFPictureShape.class::cast)
                .findFirst()
                .orElseThrow();
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(
                picture.getPictureData().getData()
            ));
            assertTrue((long) image.getWidth() * image.getHeight()
                <= 100_000);
        }
    }

    private Path sourcePdf() throws Exception {
        Path source = temporaryDirectory.resolve(
            "source-" + UUID.randomUUID() + ".pdf"
        );
        try (PDDocument document = new PDDocument()) {
            PDPage first = new PDPage(PDRectangle.LETTER);
            document.addPage(first);
            try (PDPageContentStream stream =
                     new PDPageContentStream(document, first)) {
                write(stream, 20, true, 50, 730, "Quarterly Report");
                writeRow(stream, 680, "Name", "Value");
                writeRow(stream, 650, "Revenue", "100");
                BufferedImage image = new BufferedImage(
                    80,
                    40,
                    BufferedImage.TYPE_INT_RGB
                );
                var graphics = image.createGraphics();
                graphics.setColor(Color.BLUE);
                graphics.fillRect(0, 0, 80, 40);
                graphics.dispose();
                stream.drawImage(
                    LosslessFactory.createFromImage(document, image),
                    50,
                    480,
                    80,
                    40
                );
            }
            PDPage second = new PDPage(PDRectangle.LETTER);
            document.addPage(second);
            try (PDPageContentStream stream =
                     new PDPageContentStream(document, second)) {
                write(stream, 12, false, 50, 730, "SECOND SLIDE");
            }
            document.save(source.toFile());
        }
        return source;
    }

    private Path rotatedPdf() throws Exception {
        Path source = temporaryDirectory.resolve(
            "rotated-" + UUID.randomUUID() + ".pdf"
        );
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            page.setRotation(90);
            document.addPage(page);
            try (PDPageContentStream stream =
                     new PDPageContentStream(document, page)) {
                write(stream, 12, false, 50, 700, "ROTATED PAGE");
            }
            document.save(source.toFile());
        }
        return source;
    }

    private void writeRow(
            PDPageContentStream stream,
            float y,
            String first,
            String second) throws Exception {
        write(stream, 12, false, 50, y, first);
        write(stream, 12, false, 112, y, second);
    }

    private void write(
            PDPageContentStream stream,
            float size,
            boolean bold,
            float x,
            float y,
            String text) throws Exception {
        stream.beginText();
        stream.setFont(
            new PDType1Font(bold
                ? Standard14Fonts.FontName.HELVETICA_BOLD
                : Standard14Fonts.FontName.HELVETICA),
            size
        );
        stream.newLineAtOffset(x, y);
        stream.showText(text);
        stream.endText();
    }

    private OperationContext context(Path source, String options)
            throws Exception {
        return new OperationContext(
            UUID.randomUUID(),
            objectMapper.readTree(options),
            List.of(new OperationInput(
                1,
                source,
                "report.pdf",
                "application/pdf",
                Files.size(source),
                "pdf-powerpoint-source"
            )),
            Files.createTempDirectory(
                temporaryDirectory,
                "pdf-powerpoint-context-"
            ),
            ignored -> {
            },
            () -> false
        );
    }

    private OperationSubmission.UploadDescriptor descriptor(
            String filename,
            String mediaType,
            long size) {
        return new OperationSubmission.UploadDescriptor(
            1,
            filename,
            mediaType,
            size
        );
    }

    private void assertEngineCode(
            String code,
            PdfToPowerPointProperties configured,
            String options) throws Exception {
        PdfToPowerPointOperation configuredOperation =
            new PdfToPowerPointOperation(
                new PdfToPowerPointEngine(configured),
                planFactory,
                configured
            );
        OperationException failure = assertThrows(
            OperationException.class,
            () -> configuredOperation.execute(context(
                sourcePdf(),
                options
            ))
        );
        assertEquals(code, failure.getCode());
    }
}
