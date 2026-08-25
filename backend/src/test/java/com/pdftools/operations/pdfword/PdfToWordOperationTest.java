package com.pdftools.operations.pdfword;

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
import org.apache.poi.xwpf.usermodel.XWPFDocument;
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

class PdfToWordOperationTest {

    @TempDir
    Path temporaryDirectory;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PdfToWordProperties properties = new PdfToWordProperties();
    private final PdfToWordPlanFactory planFactory =
        new PdfToWordPlanFactory();
    private final PdfToWordOperation operation = new PdfToWordOperation(
        new PdfToWordEngine(properties),
        planFactory,
        properties
    );

    @Test
    void extractsTextTableImageAndPagination() throws Exception {
        OperationOutput output = operation.execute(context(
            sourcePdf(),
            """
            {
              "mode":"editable",
              "includeImages":true,
              "detectTables":true,
              "preservePageBreaks":true
            }
            """
        )).getFirst();

        assertEquals("report.docx", output.filename());
        try (XWPFDocument document = new XWPFDocument(
                Files.newInputStream(output.path()))) {
            String text = document.getParagraphs().stream()
                .map(paragraph -> paragraph.getText())
                .reduce("", (left, right) -> left + "\n" + right);
            assertTrue(text.contains("Quarterly Report"));
            assertTrue(text.contains("SECOND PAGE"));
            assertFalse(document.getTables().isEmpty());
            assertTrue(document.getStyles().styleExist("Heading1"));
            assertEquals("Name", document.getTables().getFirst()
                .getRow(0).getCell(0).getText());
            assertEquals("100", document.getTables().getFirst()
                .getRow(1).getCell(1).getText());
            assertFalse(document.getAllPictures().isEmpty());
            assertTrue(document.getDocument().xmlText()
                .contains("nextPage"));
            var pageSize = document.getDocument().getBody()
                .getSectPr().getPgSz();
            assertEquals("12240", pageSize.getW().toString());
            assertEquals("15840", pageSize.getH().toString());
        }
    }

    @Test
    void rendersEveryPageInVisualMode() throws Exception {
        OperationOutput output = operation.execute(context(
            sourcePdf(),
            """
            {"mode":"visual","preservePageBreaks":true}
            """
        )).getFirst();

        try (XWPFDocument document = new XWPFDocument(
                Files.newInputStream(output.path()))) {
            assertEquals(2, document.getAllPictures().size());
            assertTrue(document.getDocument().xmlText()
                .contains("nextPage"));
        }
    }

    @Test
    void preservesMixedVisualPageSections() throws Exception {
        OperationOutput output = operation.execute(context(
            mixedPagePdf(),
            "{\"mode\":\"visual\",\"preservePageBreaks\":true}"
        )).getFirst();

        try (XWPFDocument document = new XWPFDocument(
                Files.newInputStream(output.path()))) {
            var firstSection = document.getParagraphs().stream()
                .filter(paragraph -> paragraph.getCTP().isSetPPr()
                    && paragraph.getCTP().getPPr().isSetSectPr())
                .findFirst()
                .orElseThrow()
                .getCTP().getPPr().getSectPr();
            assertEquals(
                "12240",
                firstSection.getPgSz().getW().toString()
            );
            assertEquals(
                "15840",
                firstSection.getPgSz().getH().toString()
            );
            var lastSize = document.getDocument().getBody()
                .getSectPr().getPgSz();
            assertEquals("16838", lastSize.getW().toString());
            assertEquals("11906", lastSize.getH().toString());
        }
    }

    @Test
    void validatesSubmissionAndRejectsEncryptedPdf() throws Exception {
        operation.validateSubmission(new OperationSubmission(
            objectMapper.readTree("{}"),
            List.of(descriptor("report.pdf", "application/pdf", 100))
        ));
        OperationException invalidType = assertThrows(
            OperationException.class,
            () -> operation.validateSubmission(new OperationSubmission(
                objectMapper.readTree("{}"),
                List.of(descriptor("report.txt", "text/plain", 100))
            ))
        );
        assertEquals("INVALID_PDF_FILE", invalidType.getCode());

        Path encrypted = temporaryDirectory.resolve("encrypted.pdf");
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            var policy = new org.apache.pdfbox.pdmodel.encryption
                .StandardProtectionPolicy(
                    "owner",
                    "user",
                    new org.apache.pdfbox.pdmodel.encryption.AccessPermission()
                );
            document.protect(policy);
            document.save(encrypted.toFile());
        }
        OperationException failure = assertThrows(
            OperationException.class,
            () -> operation.execute(context(encrypted, "{}"))
        );
        assertEquals("ENCRYPTED_PDF_NOT_SUPPORTED", failure.getCode());
    }

    @Test
    void enforcesPageTextAndImageBudgets() throws Exception {
        PdfToWordProperties pageProperties = new PdfToWordProperties();
        pageProperties.setMaxPages(1);
        assertEngineCode(
            "PDF_PAGE_LIMIT_EXCEEDED",
            pageProperties,
            "{\"mode\":\"editable\"}"
        );

        PdfToWordProperties textProperties = new PdfToWordProperties();
        textProperties.setMaxTextCharacters(5);
        assertEngineCode(
            "PDF_WORD_TEXT_LIMIT_EXCEEDED",
            textProperties,
            "{\"mode\":\"editable\"}"
        );

        PdfToWordProperties imageProperties = new PdfToWordProperties();
        imageProperties.setMaxImages(1);
        assertEngineCode(
            "PDF_WORD_IMAGE_LIMIT_EXCEEDED",
            imageProperties,
            "{\"mode\":\"visual\"}"
        );
    }

    @Test
    void preservesRotatedVisualOrderAndPageGeometry() throws Exception {
        OperationOutput output = operation.execute(context(
            rotatedPdf(),
            "{\"mode\":\"editable\"}"
        )).getFirst();

        try (XWPFDocument document = new XWPFDocument(
                Files.newInputStream(output.path()))) {
            String text = document.getParagraphs().stream()
                .map(paragraph -> paragraph.getText())
                .reduce("", (left, right) -> left + "\n" + right);
            assertTrue(
                text.indexOf("FIRST") < text.indexOf("SECOND"),
                text
            );
            var pageSize = document.getDocument().getBody()
                .getSectPr().getPgSz();
            assertEquals("15840", pageSize.getW().toString());
            assertEquals("12240", pageSize.getH().toString());
        }
    }

    @Test
    void rendersUserUnitAtConfiguredPhysicalDpi() throws Exception {
        OperationOutput output = operation.execute(context(
            userUnitPdf(),
            "{\"mode\":\"visual\"}"
        )).getFirst();

        try (XWPFDocument document = new XWPFDocument(
                Files.newInputStream(output.path()))) {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(
                document.getAllPictures().getFirst().getData()
            ));
            assertEquals(2448, image.getWidth());
            assertEquals(3168, image.getHeight());
            var pageSize = document.getDocument().getBody()
                .getSectPr().getPgSz();
            assertEquals("24480", pageSize.getW().toString());
            assertEquals("31680", pageSize.getH().toString());
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
                write(
                    stream,
                    new PDType1Font(
                        Standard14Fonts.FontName.HELVETICA_BOLD),
                    20,
                    50,
                    730,
                    "Quarterly Report"
                );
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
                write(
                    stream,
                    new PDType1Font(Standard14Fonts.FontName.HELVETICA),
                    12,
                    50,
                    730,
                    "SECOND PAGE"
                );
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
                var font = new PDType1Font(
                    Standard14Fonts.FontName.HELVETICA
                );
                write(stream, font, 12, 50, 700, "FIRST");
                write(stream, font, 12, 100, 700, "SECOND");
            }
            document.save(source.toFile());
        }
        return source;
    }

    private Path userUnitPdf() throws Exception {
        Path source = temporaryDirectory.resolve(
            "user-unit-" + UUID.randomUUID() + ".pdf"
        );
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            page.setUserUnit(2);
            document.addPage(page);
            try (PDPageContentStream stream =
                     new PDPageContentStream(document, page)) {
                write(
                    stream,
                    new PDType1Font(
                        Standard14Fonts.FontName.HELVETICA),
                    12,
                    50,
                    700,
                    "USER UNIT"
                );
            }
            document.save(source.toFile());
        }
        return source;
    }

    private Path mixedPagePdf() throws Exception {
        Path source = temporaryDirectory.resolve(
            "mixed-" + UUID.randomUUID() + ".pdf"
        );
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage(PDRectangle.LETTER));
            PDPage second = new PDPage(PDRectangle.A4);
            second.setRotation(90);
            document.addPage(second);
            document.save(source.toFile());
        }
        return source;
    }

    private void writeRow(
            PDPageContentStream stream,
            float y,
            String first,
            String second) throws Exception {
        var font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        write(stream, font, 12, 50, y, first);
        write(stream, font, 12, 112, y, second);
    }

    private void write(
            PDPageContentStream stream,
            PDType1Font font,
            float size,
            float x,
            float y,
            String text) throws Exception {
        stream.beginText();
        stream.setFont(font, size);
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
                "pdf-word-source"
            )),
            Files.createTempDirectory(
                temporaryDirectory,
                "pdf-word-context-"
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
            PdfToWordProperties configured,
            String options) throws Exception {
        PdfToWordOperation configuredOperation = new PdfToWordOperation(
            new PdfToWordEngine(configured),
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
