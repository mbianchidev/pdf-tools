package com.pdftools.operations.compress;

import com.pdftools.operations.OperationContext;
import com.pdftools.operations.OperationException;
import com.pdftools.operations.OperationInput;
import com.pdftools.operations.OperationOutput;
import com.pdftools.operations.OperationSubmission;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompressPdfOperationTest {

    @TempDir
    Path temporaryDirectory;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CompressPdfProperties properties =
        new CompressPdfProperties();
    private final CompressPdfPlanFactory planFactory =
        new CompressPdfPlanFactory();
    private final CompressPdfOperation operation =
        new CompressPdfOperation(
            new CompressPdfEngine(properties),
            planFactory,
            properties
        );

    @Test
    void preservesACompactPdfWhenRewriteWouldNotSaveSpace()
            throws Exception {
        Path source = textPdf(false);

        OperationOutput output = operation.execute(context(
            source,
            "{\"mode\":\"low\"}"
        )).getFirst();

        assertEquals("report-compressed.pdf", output.filename());
        assertTrue(Files.size(output.path()) <= Files.size(source));
        if (Files.size(output.path()) == Files.size(source)) {
            assertArrayEquals(
                Files.readAllBytes(source),
                Files.readAllBytes(output.path())
            );
        }
        try (PDDocument result = Loader.loadPDF(output.path().toFile())) {
            assertEquals(1, result.getNumberOfPages());
            String text = new PDFTextStripper().getText(result);
            assertTrue(
                text.replaceAll("\\s+", "").contains("SEARCHABLE"),
                () -> "extracted text: " + text
            );
        }
    }

    @Test
    void recommendedAndExtremeModesCompressImagesButKeepText()
            throws Exception {
        Path source = imagePdf();

        OperationOutput recommended = operation.execute(context(
            source,
            "{\"mode\":\"recommended\"}"
        )).getFirst();
        OperationOutput extreme = operation.execute(context(
            source,
            "{\"mode\":\"extreme\"}"
        )).getFirst();
        OperationOutput repeated = operation.execute(context(
            source,
            "{\"mode\":\"recommended\"}"
        )).getFirst();

        assertTrue(
            Files.size(recommended.path()) < Files.size(source),
            () -> "recommended=" + size(recommended.path())
                + ", source=" + size(source)
        );
        assertTrue(
            Files.size(extreme.path()) <= Files.size(recommended.path()),
            () -> "extreme=" + size(extreme.path())
                + ", recommended=" + size(recommended.path())
        );
        assertArrayEquals(
            Files.readAllBytes(recommended.path()),
            Files.readAllBytes(repeated.path())
        );
        try (PDDocument result =
                 Loader.loadPDF(recommended.path().toFile())) {
            assertEquals(1, result.getNumberOfPages());
            assertEquals(90, result.getPage(0).getRotation());
            assertEquals(
                PDRectangle.LETTER.getWidth(),
                result.getPage(0).getMediaBox().getWidth()
            );
            String text = new PDFTextStripper().getText(result);
            assertTrue(
                text.replaceAll("\\s+", "").contains("SEARCHABLE"),
                () -> "extracted text: " + text
            );
        }
    }

    @Test
    void validatesSubmissionAndRejectsEncryptedPdf() throws Exception {
        operation.validateSubmission(new OperationSubmission(
            objectMapper.readTree("{\"mode\":\"recommended\"}"),
            List.of(descriptor("report.pdf", "application/pdf", 100))
        ));
        OperationException invalid = assertThrows(
            OperationException.class,
            () -> operation.validateSubmission(new OperationSubmission(
                objectMapper.readTree("{\"mode\":\"maximum\"}"),
                List.of(descriptor("report.pdf", "application/pdf", 100))
            ))
        );
        assertEquals("INVALID_COMPRESSION_MODE", invalid.getCode());

        Path encrypted = textPdf(true);
        OperationException encryptedFailure = assertThrows(
            OperationException.class,
            () -> operation.execute(context(
                encrypted,
                "{\"mode\":\"low\"}"
            ))
        );
        assertEquals(
            "ENCRYPTED_PDF_NOT_SUPPORTED",
            encryptedFailure.getCode()
        );
    }

    private Path imagePdf() throws Exception {
        Path source = temporaryDirectory.resolve("image-source.pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            page.setRotation(90);
            document.addPage(page);
            BufferedImage image = new BufferedImage(
                1_600,
                1_200,
                BufferedImage.TYPE_INT_RGB
            );
            int noise = 0x13579bdf;
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    noise ^= noise << 13;
                    noise ^= noise >>> 17;
                    noise ^= noise << 5;
                    image.setRGB(x, y, noise & 0x00ffffff);
                }
            }
            try (PDPageContentStream stream =
                     new PDPageContentStream(document, page)) {
                stream.drawImage(
                    LosslessFactory.createFromImage(document, image),
                    20,
                    20,
                    500,
                    400
                );
                writeText(stream);
            } finally {
                image.flush();
            }
            document.save(source.toFile());
        }
        return source;
    }

    private Path textPdf(boolean encrypted) throws Exception {
        Path source = temporaryDirectory.resolve(
            encrypted ? "encrypted.pdf" : "text.pdf"
        );
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream stream =
                     new PDPageContentStream(document, page)) {
                writeText(stream);
            }
            if (encrypted) {
                document.protect(new StandardProtectionPolicy(
                    "owner",
                    "user",
                    new AccessPermission()
                ));
            }
            document.save(source.toFile());
        }
        return source;
    }

    private void writeText(PDPageContentStream stream) throws Exception {
        stream.beginText();
        stream.setFont(
            new PDType1Font(Standard14Fonts.FontName.HELVETICA),
            12
        );
        stream.newLineAtOffset(50, 700);
        stream.showText("SEARCHABLE");
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
                sha256(source)
            )),
            Files.createTempDirectory(
                temporaryDirectory,
                "compress-context-"
            ),
            ignored -> {
            },
            () -> false
        );
    }

    private String sha256(Path source) throws Exception {
        return HexFormat.of().formatHex(
            MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(source))
        );
    }

    private long size(Path path) {
        try {
            return Files.size(path);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
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
