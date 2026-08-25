package com.pdftools.operations.pdfmarkdown;

import com.pdftools.operations.OperationContext;
import com.pdftools.operations.OperationException;
import com.pdftools.operations.OperationInput;
import com.pdftools.operations.OperationOutput;
import com.pdftools.operations.OperationSubmission;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfToMarkdownOperationTest {

    @TempDir
    Path temporaryDirectory;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PdfToMarkdownProperties properties =
        new PdfToMarkdownProperties();
    private final PdfToMarkdownPlanFactory planFactory =
        new PdfToMarkdownPlanFactory();
    private final PdfToMarkdownOperation operation =
        new PdfToMarkdownOperation(
            new PdfToMarkdownEngine(properties),
            planFactory,
            properties
        );

    @Test
    void createsStructuredMarkdownAndImageBundle() throws Exception {
        OperationOutput output = operation.execute(context(
            structuredPdf(),
            "{}"
        )).getFirst();

        assertEquals("report-markdown.zip", output.filename());
        try (ZipFile bundle = new ZipFile(output.path().toFile())) {
            String markdown = new String(
                bundle.getInputStream(
                    bundle.getEntry("document.md")
                ).readAllBytes(),
                StandardCharsets.UTF_8
            );
            assertTrue(markdown.contains("# Report Title"));
            assertTrue(markdown.contains("- First item"));
            assertTrue(markdown.contains("| Name | Value |"));
            assertTrue(markdown.contains("| Revenue | 100 |"));
            assertTrue(markdown.contains(
                "![Page 1 image 1](images/page-001-image-001.png)"
            ));
            assertTrue(markdown.contains("<!-- Page 2 -->"));
            assertTrue(markdown.contains("SECOND PAGE"));
            assertTrue(
                bundle.getEntry("images/page-001-image-001.png")
                    .getSize() > 0
            );
        }
    }

    @Test
    void rejectsImageOnlyPdfEvenWhenImagesAreDisabled() throws Exception {
        OperationException failure = assertThrows(
            OperationException.class,
            () -> operation.execute(context(
                imageOnlyPdf(),
                "{\"includeImages\":false}"
            ))
        );

        assertEquals("IMAGE_ONLY_PDF_NOT_SUPPORTED", failure.getCode());
        assertTrue(failure.getMessage().contains("extractable text"));
    }

    @Test
    void disablesPageMarkersAndEscapesMarkdownInjection() throws Exception {
        Path source = temporaryDirectory.resolve("plain-markdown.pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage first = new PDPage();
            document.addPage(first);
            try (PDPageContentStream stream =
                     new PDPageContentStream(document, first)) {
                write(stream, 12, false, 50, 730, "- Not a list");
                write(stream, 12, false, 50, 700, "<script>");
            }
            PDPage second = new PDPage();
            document.addPage(second);
            try (PDPageContentStream stream =
                     new PDPageContentStream(document, second)) {
                write(stream, 12, false, 50, 730, "2. Not a list");
            }
            document.save(source.toFile());
        }

        OperationOutput output = operation.execute(context(
            source,
            """
                {
                  "detectHeadings":false,
                  "detectLists":false,
                  "detectTables":false,
                  "includeImages":false,
                  "preservePageBreaks":false
                }
                """
        )).getFirst();

        try (ZipFile bundle = new ZipFile(output.path().toFile())) {
            String markdown = new String(
                bundle.getInputStream(bundle.getEntry("document.md"))
                    .readAllBytes(),
                StandardCharsets.UTF_8
            );
            assertTrue(markdown.contains("\\- Not a list"));
            assertTrue(markdown.contains("&lt;script&gt;"));
            assertTrue(markdown.contains("2\\. Not a list"));
            assertTrue(!markdown.contains("<!-- Page"));
            assertTrue(!markdown.contains("\n---\n"));
        }
    }

    @Test
    void validatesSubmissionAndEncryptedPdf() throws Exception {
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
        OperationException encryptedFailure = assertThrows(
            OperationException.class,
            () -> operation.execute(context(encrypted, "{}"))
        );
        assertEquals(
            "ENCRYPTED_PDF_NOT_SUPPORTED",
            encryptedFailure.getCode()
        );
    }

    private Path structuredPdf() throws Exception {
        Path source = temporaryDirectory.resolve(
            "structured-" + UUID.randomUUID() + ".pdf"
        );
        try (PDDocument document = new PDDocument()) {
            PDPage first = new PDPage();
            document.addPage(first);
            try (PDPageContentStream stream =
                     new PDPageContentStream(document, first)) {
                write(stream, 20, true, 50, 730, "Report Title");
                write(stream, 12, false, 50, 690, "- First item");
                writeRow(stream, 650, "Name", "Value");
                writeRow(stream, 620, "Revenue", "100");
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
            PDPage second = new PDPage();
            document.addPage(second);
            try (PDPageContentStream stream =
                     new PDPageContentStream(document, second)) {
                write(stream, 12, false, 50, 730, "SECOND PAGE");
            }
            document.save(source.toFile());
        }
        return source;
    }

    private Path imageOnlyPdf() throws Exception {
        Path source = temporaryDirectory.resolve("image-only.pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            BufferedImage image = new BufferedImage(
                20,
                20,
                BufferedImage.TYPE_INT_RGB
            );
            try (PDPageContentStream stream =
                     new PDPageContentStream(document, page)) {
                stream.drawImage(
                    LosslessFactory.createFromImage(document, image),
                    50,
                    600,
                    100,
                    100
                );
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
                "pdf-markdown-source"
            )),
            Files.createTempDirectory(
                temporaryDirectory,
                "pdf-markdown-context-"
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
}
