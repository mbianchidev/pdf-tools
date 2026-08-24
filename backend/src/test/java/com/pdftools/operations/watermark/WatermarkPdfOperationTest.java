package com.pdftools.operations.watermark;

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
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WatermarkPdfOperationTest {

    @TempDir
    Path temporaryDirectory;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SplitProperties splitProperties = new SplitProperties();
    private final PageExpressionParser pageParser =
        new PageExpressionParser();
    private final WatermarkProperties properties = new WatermarkProperties();
    private final JpegValidationProperties validationProperties =
        new JpegValidationProperties();
    private final WatermarkPdfOperation operation = new WatermarkPdfOperation(
        new PdfSplitEngine(
            new SplitPlanFactory(pageParser, splitProperties),
            splitProperties
        ),
        new WatermarkPlanFactory(pageParser),
        new WatermarkImagePreparer(
            properties,
            new JpegValidationService(validationProperties),
            validationProperties,
            new JpegPdfImageFactory()
        ),
        new WatermarkRenderer(),
        properties
    );

    @Test
    void appliesStyledTextWithRealOpacityToSelectedPages()
            throws Exception {
        OperationOutput output = operation.execute(context(
            List.of(sourcePdf()),
            List.of("source.pdf"),
            List.of("application/pdf"),
            """
            {
              "mode":"text",
              "pages":"2",
              "text":"WM",
              "font":"helvetica-bold",
              "fontSize":40,
              "color":"#ff0000",
              "opacity":0.25,
              "rotation":0,
              "x":0.5,
              "y":0.5
            }
            """
        )).getFirst();

        assertEquals("source_watermarked.pdf", output.filename());
        try (PDDocument document = Loader.loadPDF(output.path().toFile())) {
            PDFRenderer renderer = new PDFRenderer(document);
            BufferedImage first = renderer.renderImage(0, 2);
            BufferedImage second = renderer.renderImage(1, 2);
            assertEquals(255, minimumGreen(first));
            int blendedGreen = minimumGreen(second);
            assertTrue(blendedGreen >= 150 && blendedGreen <= 220);
        }
    }

    @Test
    void appliesImageWatermarkAtRequestedSizeAndPosition()
            throws Exception {
        Path image = png(Color.BLUE);

        OperationOutput output = operation.execute(context(
            List.of(sourcePdf(), image),
            List.of("source.pdf", "mark.png"),
            List.of("application/pdf", "image/png"),
            """
            {
              "mode":"image",
              "pages":"1",
              "imageWidthPercent":50,
              "opacity":1,
              "rotation":0,
              "x":0.5,
              "y":0.5
            }
            """
        )).getFirst();

        try (PDDocument document = Loader.loadPDF(output.path().toFile())) {
            BufferedImage rendered = new PDFRenderer(document).renderImage(
                0,
                2
            );
            Color center = new Color(rendered.getRGB(
                rendered.getWidth() / 2,
                rendered.getHeight() / 2
            ));
            assertTrue(center.getBlue() > 220);
            assertTrue(center.getRed() < 30);
            assertTrue(center.getGreen() < 30);
        }
    }

    @Test
    void placesJpegOnRotatedTranslatedUserUnitPage()
            throws Exception {
        Path image = jpeg(Color.RED);

        OperationOutput output = operation.execute(context(
            List.of(rotatedSourcePdf(), image),
            List.of("rotated.pdf", "mark.jpg"),
            List.of("application/pdf", "image/jpeg"),
            """
            {
              "mode":"image",
              "pages":"1",
              "imageWidthPercent":20,
              "opacity":1,
              "rotation":0,
              "x":0.25,
              "y":0.25
            }
            """
        )).getFirst();

        try (PDDocument document = Loader.loadPDF(output.path().toFile())) {
            BufferedImage rendered = new PDFRenderer(document).renderImage(
                0,
                2
            );
            Color positioned = new Color(rendered.getRGB(
                rendered.getWidth() / 4,
                rendered.getHeight() / 4
            ));
            assertTrue(positioned.getRed() > 220);
            assertTrue(positioned.getGreen() < 35);
            assertTrue(positioned.getBlue() < 35);
        }
    }

    @Test
    void validatesModeFilesAndStyleOptions() throws Exception {
        OperationSubmission.UploadDescriptor pdf = descriptor(
            1,
            "source.pdf",
            "application/pdf"
        );
        OperationSubmission.UploadDescriptor image = descriptor(
            2,
            "mark.png",
            "image/png"
        );
        assertSubmissionCode(
            "WATERMARK_TEXT_REQUIRED",
            """
            {"mode":"text","text":""}
            """,
            List.of(pdf)
        );
        assertSubmissionCode(
            "INVALID_FILE_COUNT",
            """
            {"mode":"image"}
            """,
            List.of(pdf)
        );
        assertSubmissionCode(
            "INVALID_WATERMARK_OPACITY",
            """
            {"mode":"image","opacity":0}
            """,
            List.of(pdf, image)
        );
        assertSubmissionCode(
            "INVALID_WATERMARK_COLOR",
            """
            {"mode":"text","text":"WM","color":"red"}
            """,
            List.of(pdf)
        );
    }

    private int minimumGreen(BufferedImage image) {
        int minimum = 255;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                minimum = Math.min(
                    minimum,
                    new Color(image.getRGB(x, y)).getGreen()
                );
            }
        }
        return minimum;
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
            BufferedImage.TYPE_INT_ARGB
        );
        java.awt.Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(color);
            graphics.fillRect(0, 0, 40, 20);
        } finally {
            graphics.dispose();
        }
        Path path = temporaryDirectory.resolve("mark.png");
        ImageIO.write(image, "png", path.toFile());
        image.flush();
        return path;
    }

    private Path jpeg(Color color) throws Exception {
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
        Path path = temporaryDirectory.resolve("mark.jpg");
        ImageIO.write(image, "jpeg", path.toFile());
        image.flush();
        return path;
    }

    private Path rotatedSourcePdf() throws Exception {
        Path source = temporaryDirectory.resolve("rotated.pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(new PDRectangle(300, 300));
            page.setCropBox(new PDRectangle(10, 20, 200, 100));
            page.setRotation(90);
            page.setUserUnit(2);
            document.addPage(page);
            try (PDPageContentStream content =
                    new PDPageContentStream(document, page)) {
                content.setNonStrokingColor(Color.WHITE);
                content.addRect(10, 20, 200, 100);
                content.fill();
            }
            document.save(source.toFile());
        }
        return source;
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
                "watermark-context-"
            ),
            ignored -> {
            },
            () -> false
        );
    }
}
