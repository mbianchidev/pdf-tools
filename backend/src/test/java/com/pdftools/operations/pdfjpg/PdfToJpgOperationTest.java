package com.pdftools.operations.pdfjpg;

import com.pdftools.operations.OperationContext;
import com.pdftools.operations.OperationException;
import com.pdftools.operations.OperationInput;
import com.pdftools.operations.OperationOutput;
import com.pdftools.operations.OperationSubmission;
import com.pdftools.operations.ZipArtifactService;
import com.pdftools.operations.shared.pages.PageExpressionParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Enumeration;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfToJpgOperationTest {

    @TempDir
    Path temporaryDirectory;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PdfToJpgProperties properties = new PdfToJpgProperties();
    private final PdfToJpgPlanFactory planFactory = new PdfToJpgPlanFactory(
        new PageExpressionParser(),
        properties
    );
    private final PdfToJpgOperation operation = new PdfToJpgOperation(
        new PdfToJpgEngine(planFactory, properties),
        new ZipArtifactService(),
        properties,
        planFactory
    );

    @Test
    void rendersSelectedPagesAtRequestedResolutionInDocumentOrder()
            throws Exception {
        Path source = coloredPdf();

        OperationOutput output = operation.execute(context(
            source,
            """
            {
              "pages":"3,1-2",
              "dpi":144,
              "quality":100
            }
            """
        )).getFirst();

        assertEquals("source_jpg.zip", output.filename());
        assertEquals("application/zip", output.mediaType());
        try (ZipFile zip = new ZipFile(output.path().toFile())) {
            assertEquals(
                List.of(
                    "source_page_0001.jpg",
                    "source_page_0002.jpg",
                    "source_page_0003.jpg"
                ),
                entryNames(zip)
            );
            assertImage(zip, "source_page_0001.jpg", 144, 72, Color.RED);
            assertImage(zip, "source_page_0002.jpg", 144, 72, Color.GREEN);
            assertImage(zip, "source_page_0003.jpg", 144, 72, Color.BLUE);
        }
    }

    @Test
    void producesDeterministicArchiveBytes() throws Exception {
        Path source = coloredPdf();
        String options = """
            {"pages":"1-2","dpi":96,"quality":85}
            """;

        OperationOutput first = operation.execute(context(source, options))
            .getFirst();
        OperationOutput second = operation.execute(context(source, options))
            .getFirst();

        assertArrayEquals(
            Files.readAllBytes(first.path()),
            Files.readAllBytes(second.path())
        );
    }

    @Test
    void appliesJpegQualityToEncodedImageSize() throws Exception {
        Path source = detailedPdf();

        OperationOutput low = operation.execute(context(
            source,
            """
            {"pages":"1","dpi":72,"quality":10}
            """
        )).getFirst();
        OperationOutput high = operation.execute(context(
            source,
            """
            {"pages":"1","dpi":72,"quality":100}
            """
        )).getFirst();

        try (ZipFile lowZip = new ZipFile(low.path().toFile());
             ZipFile highZip = new ZipFile(high.path().toFile())) {
            assertTrue(
                highZip.getEntry("source_page_0001.jpg").getSize()
                    > lowZip.getEntry("source_page_0001.jpg").getSize()
            );
        }
    }

    @Test
    void rejectsInvalidControlsEncryptedInputAndOversizedRendering()
            throws Exception {
        OperationSubmission.UploadDescriptor pdf =
            new OperationSubmission.UploadDescriptor(
                1,
                "source.pdf",
                "application/pdf",
                100
            );
        assertSubmissionCode(
            "INVALID_JPG_DPI",
            """
            {"dpi":301}
            """,
            pdf
        );
        assertSubmissionCode(
            "INVALID_JPG_QUALITY",
            """
            {"quality":9}
            """,
            pdf
        );
        assertCode(
            "DUPLICATE_PAGE",
            coloredPdf(),
            """
            {"pages":"1,1"}
            """
        );
        assertCode(
            "ENCRYPTED_PDF",
            encryptedPdf(),
            """
            {"pages":"all"}
            """
        );
        assertCode(
            "ENCRYPTED_PDF",
            emptyUserPasswordEncryptedPdf(),
            """
            {"pages":"all"}
            """
        );

        properties.setMaxPixelsPerPage(100);
        assertCode(
            "JPG_RENDER_SIZE_LIMIT_EXCEEDED",
            coloredPdf(),
            """
            {"pages":"1","dpi":72}
            """
        );
    }

    @Test
    void appliesUserUnitToPhysicalDpi() throws Exception {
        OperationOutput output = operation.execute(context(
            userUnitPdf(),
            """
            {"pages":"1","dpi":72,"quality":90}
            """
        )).getFirst();

        try (ZipFile zip = new ZipFile(output.path().toFile())) {
            BufferedImage image = ImageIO.read(zip.getInputStream(
                zip.getEntry("source_page_0001.jpg")
            ));
            assertEquals(144, image.getWidth());
            assertEquals(72, image.getHeight());
        }
    }

    @Test
    void propagatesConfiguredDpiRangeToIsolatedWorker() throws Exception {
        properties.setMaxDpi(600);

        OperationOutput output = operation.execute(context(
            coloredPdf(),
            """
            {"pages":"1","dpi":600,"quality":85}
            """
        )).getFirst();

        try (ZipFile zip = new ZipFile(output.path().toFile())) {
            BufferedImage image = ImageIO.read(zip.getInputStream(
                zip.getEntry("source_page_0001.jpg")
            ));
            assertEquals(600, image.getWidth());
            assertEquals(300, image.getHeight());
        }
    }

    @Test
    void reservesPageSuffixForLongSourceFilenames() throws Exception {
        String sourceName = "x".repeat(116) + ".pdf";

        OperationOutput output = operation.execute(context(
            coloredPdf(),
            """
            {"pages":"1-2","dpi":72,"quality":85}
            """,
            sourceName
        )).getFirst();

        try (ZipFile zip = new ZipFile(output.path().toFile())) {
            List<String> names = entryNames(zip);
            assertTrue(names.get(0).endsWith("_page_0001.jpg"));
            assertTrue(names.get(1).endsWith("_page_0002.jpg"));
        }
    }

    @Test
    void terminatesIsolatedRendererAtConfiguredTimeout() throws Exception {
        properties.setWorkerTimeout(Duration.ofMillis(1));

        assertCode(
            "JPG_RENDER_TIMEOUT",
            coloredPdf(),
            """
            {"pages":"1","dpi":72,"quality":85}
            """
        );
    }

    private void assertImage(
            ZipFile zip,
            String name,
            int width,
            int height,
            Color expected) throws Exception {
        BufferedImage image = ImageIO.read(zip.getInputStream(
            zip.getEntry(name)
        ));
        assertEquals(width, image.getWidth());
        assertEquals(height, image.getHeight());
        Color actual = new Color(image.getRGB(width / 2, height / 2));
        assertTrue(Math.abs(actual.getRed() - expected.getRed()) <= 8);
        assertTrue(Math.abs(actual.getGreen() - expected.getGreen()) <= 8);
        assertTrue(Math.abs(actual.getBlue() - expected.getBlue()) <= 8);
    }

    private List<String> entryNames(ZipFile zip) {
        Enumeration<? extends ZipEntry> entries = zip.entries();
        java.util.ArrayList<String> names = new java.util.ArrayList<>();
        while (entries.hasMoreElements()) {
            names.add(entries.nextElement().getName());
        }
        return List.copyOf(names);
    }

    private void assertSubmissionCode(
            String code,
            String options,
            OperationSubmission.UploadDescriptor pdf) throws Exception {
        OperationException exception = assertThrows(
            OperationException.class,
            () -> operation.validateSubmission(new OperationSubmission(
                objectMapper.readTree(options),
                List.of(pdf)
            ))
        );
        assertEquals(code, exception.getCode());
    }

    private void assertCode(
            String code,
            Path source,
            String options) throws Exception {
        OperationException exception = assertThrows(
            OperationException.class,
            () -> operation.execute(context(source, options))
        );
        assertEquals(code, exception.getCode());
    }

    private Path coloredPdf() throws Exception {
        Path source = temporaryDirectory.resolve(
            "source-" + UUID.randomUUID() + ".pdf"
        );
        try (PDDocument document = new PDDocument()) {
            addColorPage(document, Color.RED);
            addColorPage(document, Color.GREEN);
            addColorPage(document, Color.BLUE);
            document.save(source.toFile());
        }
        return source;
    }

    private Path detailedPdf() throws Exception {
        Path source = temporaryDirectory.resolve("detailed.pdf");
        BufferedImage image = new BufferedImage(
            256,
            256,
            BufferedImage.TYPE_INT_RGB
        );
        Random random = new Random(42);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                image.setRGB(x, y, random.nextInt(0x1000000));
            }
        }
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(new PDRectangle(256, 256));
            document.addPage(page);
            try (PDPageContentStream content =
                    new PDPageContentStream(document, page)) {
                content.drawImage(
                    LosslessFactory.createFromImage(document, image),
                    0,
                    0,
                    256,
                    256
                );
            }
            document.save(source.toFile());
        } finally {
            image.flush();
        }
        return source;
    }

    private void addColorPage(PDDocument document, Color color)
            throws Exception {
        PDPage page = new PDPage(new PDRectangle(72, 36));
        document.addPage(page);
        try (PDPageContentStream content =
                new PDPageContentStream(document, page)) {
            content.setNonStrokingColor(color);
            content.addRect(0, 0, 72, 36);
            content.fill();
        }
    }

    private Path encryptedPdf() throws Exception {
        Path source = coloredPdf();
        try (PDDocument document =
                org.apache.pdfbox.Loader.loadPDF(source.toFile())) {
            StandardProtectionPolicy policy = new StandardProtectionPolicy(
                "owner-secret",
                "open-secret",
                new AccessPermission()
            );
            policy.setEncryptionKeyLength(256);
            policy.setPreferAES(true);
            document.protect(policy);
            Path encrypted = temporaryDirectory.resolve("encrypted.pdf");
            document.save(encrypted.toFile());
            return encrypted;
        }
    }

    private Path emptyUserPasswordEncryptedPdf() throws Exception {
        Path source = coloredPdf();
        try (PDDocument document =
                org.apache.pdfbox.Loader.loadPDF(source.toFile())) {
            StandardProtectionPolicy policy = new StandardProtectionPolicy(
                "owner-secret",
                "",
                new AccessPermission()
            );
            policy.setEncryptionKeyLength(256);
            policy.setPreferAES(true);
            document.protect(policy);
            Path encrypted = temporaryDirectory.resolve(
                "empty-user-encrypted.pdf"
            );
            document.save(encrypted.toFile());
            return encrypted;
        }
    }

    private Path userUnitPdf() throws Exception {
        Path source = temporaryDirectory.resolve("user-unit.pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(new PDRectangle(72, 36));
            page.setUserUnit(2);
            document.addPage(page);
            document.save(source.toFile());
        }
        return source;
    }

    private OperationContext context(Path source, String options)
            throws Exception {
        return context(source, options, "source.pdf");
    }

    private OperationContext context(
            Path source,
            String options,
            String sourceFilename) throws Exception {
        return new OperationContext(
            UUID.randomUUID(),
            objectMapper.readTree(options),
            List.of(new OperationInput(
                1,
                source,
                sourceFilename,
                "application/pdf",
                Files.size(source),
                "test-sha"
            )),
            Files.createTempDirectory(temporaryDirectory, "pdf-jpg-context-"),
            ignored -> {
            },
            () -> false
        );
    }
}
