package com.pdftools.operations.pagenumbers;

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
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PageNumbersPdfOperationTest {

    @TempDir
    Path temporaryDirectory;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SplitProperties properties = new SplitProperties();
    private final PageExpressionParser parser = new PageExpressionParser();
    private final PageNumbersPdfOperation operation =
        new PageNumbersPdfOperation(
            new PdfSplitEngine(
                new SplitPlanFactory(parser, properties),
                properties
            ),
            new PageNumbersPlanFactory(parser)
        );

    @Test
    void addsRangedNumbersWithStartTemplateFontAndPosition()
            throws Exception {
        OperationOutput output = operation.execute(context(
            sourcePdf(),
            """
            {
              "pages":"2-3",
              "start":5,
              "template":"Page {page} of {total}",
              "font":"helvetica-bold",
              "fontSize":12,
              "position":"bottom-right",
              "margin":10
            }
            """
        )).getFirst();

        assertEquals("source_numbered.pdf", output.filename());
        try (PDDocument document = Loader.loadPDF(output.path().toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(1);
            stripper.setEndPage(1);
            assertTrue(stripper.getText(document).isBlank());
            stripper.setStartPage(2);
            stripper.setEndPage(2);
            assertTrue(stripper.getText(document).contains("Page 5 of 3"));
            stripper.setStartPage(3);
            stripper.setEndPage(3);
            assertTrue(stripper.getText(document).contains("Page 6 of 3"));

            BufferedImage rendered = new PDFRenderer(document).renderImage(2);
            assertTrue(hasInk(
                rendered,
                rendered.getWidth() / 2,
                rendered.getWidth(),
                rendered.getHeight() / 2,
                rendered.getHeight()
            ));
        }
    }

    @Test
    void supportsBuiltInTemplates() throws Exception {
        OperationOutput output = operation.execute(context(
            sourcePdf(),
            """
            {
              "start":1,
              "template":"{page} / {total}",
              "font":"courier",
              "fontSize":10,
              "position":"top-center",
              "margin":8
            }
            """
        )).getFirst();
        try (PDDocument document = Loader.loadPDF(output.path().toFile())) {
            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("1 / 3"));
            assertTrue(text.contains("2 / 3"));
            assertTrue(text.contains("3 / 3"));
        }
    }

    @Test
    void convertsPointSizesAndMarginsThroughPageUserUnit() throws Exception {
        Path source = temporaryDirectory.resolve("user-unit.pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(new PDRectangle(200, 100));
            page.setUserUnit(2);
            document.addPage(page);
            document.save(source.toFile());
        }

        OperationOutput output = operation.execute(context(
            source,
            """
            {
              "start":1,
              "template":"{page}",
              "font":"helvetica",
              "fontSize":12,
              "position":"bottom-left",
              "margin":20
            }
            """
        )).getFirst();
        try (PDDocument document = Loader.loadPDF(output.path().toFile())) {
            String content;
            try (var input = document.getPage(0).getContents()) {
                content = new String(
                    input.readAllBytes(),
                    StandardCharsets.US_ASCII
                );
            }
            assertTrue(content.matches("(?s).*\\s6(?:\\.0)?\\s+Tf.*"));
            assertTrue(content.matches(
                "(?s).*\\s10(?:\\.0)?\\s+10(?:\\.0)?\\s+Tm.*"
            ));
        }
    }

    @Test
    void rejectsInvalidTemplatesFontsPositionsAndRanges() throws Exception {
        assertCode(
            "INVALID_PAGE_NUMBER_TEMPLATE",
            baseOptions("\"template\":\"{unknown}\"")
        );
        assertCode(
            "INVALID_PAGE_NUMBER_FONT",
            baseOptions("\"font\":\"comic-sans\"")
        );
        assertCode(
            "INVALID_PAGE_NUMBER_POSITION",
            baseOptions("\"position\":\"middle\"")
        );
        assertCode(
            "DUPLICATE_PAGE",
            baseOptions("\"pages\":\"1,1\"")
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
            "INVALID_PAGE_NUMBER_TEMPLATE",
            assertThrows(
                OperationException.class,
                () -> operation.validateSubmission(new OperationSubmission(
                    objectMapper.readTree("""
                        {
                          "start":1,
                          "template":"",
                          "font":"helvetica",
                          "fontSize":12,
                          "position":"bottom-center",
                          "margin":12
                        }
                        """),
                    List.of(pdf)
                ))
            ).getCode()
        );

        Path source = sourcePdf();
        String options = baseOptions("\"pages\":\"odd\"");
        OperationOutput first = operation.execute(context(source, options))
            .getFirst();
        OperationOutput second = operation.execute(context(source, options))
            .getFirst();
        assertArrayEquals(
            Files.readAllBytes(first.path()),
            Files.readAllBytes(second.path())
        );
    }

    private boolean hasInk(
            BufferedImage image,
            int minX,
            int maxX,
            int minY,
            int maxY) {
        for (int y = minY; y < maxY; y++) {
            for (int x = minX; x < maxX; x++) {
                if ((image.getRGB(x, y) & 0x00ffffff) != 0x00ffffff) {
                    return true;
                }
            }
        }
        return false;
    }

    private String baseOptions(String extra) {
        return """
            {
              "start":1,
              "template":"{page}",
              "font":"helvetica",
              "fontSize":12,
              "position":"bottom-center",
              "margin":12,
            """
            + extra
            + "}";
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
            document.addPage(new PDPage(new PDRectangle(200, 100)));
            PDPage rotated = new PDPage(new PDRectangle(200, 100));
            rotated.setRotation(90);
            document.addPage(rotated);
            document.addPage(new PDPage(new PDRectangle(200, 100)));
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
            Files.createTempDirectory(
                temporaryDirectory,
                "page-numbers-context-"
            ),
            ignored -> {
            },
            () -> false
        );
    }
}
