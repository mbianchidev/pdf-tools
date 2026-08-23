package com.pdftools.operations.merge;

import com.pdftools.operations.OperationContext;
import com.pdftools.operations.OperationException;
import com.pdftools.operations.OperationInput;
import com.pdftools.operations.OperationOutput;
import com.pdftools.operations.OperationSubmission;
import com.pdftools.testing.PdfTestFixtures;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MergePdfOperationTest {

    @TempDir
    Path temporaryDirectory;

    private final MergeProperties properties = new MergeProperties();
    private final MergePdfOperation operation = new MergePdfOperation(
        new PdfMergeEngine(properties),
        properties
    );

    @Test
    void mergesInExplicitPositionOrderWithGoldenRendering() throws Exception {
        Path red = PdfTestFixtures.coloredPdf(
            temporaryDirectory.resolve("red.pdf"),
            List.of(new PdfTestFixtures.PageSpec(200, 300, Color.RED))
        );
        Path blue = PdfTestFixtures.coloredPdf(
            temporaryDirectory.resolve("blue.pdf"),
            List.of(new PdfTestFixtures.PageSpec(300, 200, Color.BLUE))
        );
        OperationContext context = context(List.of(
            input(2, blue, "blue.pdf"),
            input(1, red, "red.pdf")
        ), "{\"outputFilename\":\"ordered.pdf\"}");

        List<OperationOutput> outputs = operation.execute(context);

        assertEquals("ordered.pdf", outputs.getFirst().filename());
        try (PDDocument merged = Loader.loadPDF(outputs.getFirst().path().toFile())) {
            assertEquals(2, merged.getNumberOfPages());
            assertEquals(200, merged.getPage(0).getMediaBox().getWidth());
            assertEquals(300, merged.getPage(1).getMediaBox().getWidth());
            assertCenterColor(new PDFRenderer(merged).renderImage(0), Color.RED);
            assertCenterColor(new PDFRenderer(merged).renderImage(1), Color.BLUE);
        }
    }

    @Test
    void rejectsInvalidAndEncryptedInputsWithStructuredCodes() throws Exception {
        Path invalid = temporaryDirectory.resolve("invalid.pdf");
        Files.writeString(invalid, "not a PDF");
        OperationException invalidError = assertThrows(
            OperationException.class,
            () -> operation.execute(context(List.of(
                input(1, invalid, "invalid.pdf"),
                input(2, invalid, "invalid-2.pdf")
            ), "{}"))
        );
        assertEquals("INVALID_PDF", invalidError.getCode());

        Path encrypted = temporaryDirectory.resolve("encrypted.pdf");
        try (PDDocument document = new PDDocument()) {
            document.addPage(new org.apache.pdfbox.pdmodel.PDPage());
            document.protect(new StandardProtectionPolicy(
                "owner-password",
                "user-password",
                new AccessPermission()
            ));
            document.save(encrypted.toFile());
        }
        OperationException encryptedError = assertThrows(
            OperationException.class,
            () -> operation.execute(context(List.of(
                input(1, encrypted, "encrypted.pdf"),
                input(2, encrypted, "encrypted-2.pdf")
            ), "{}"))
        );
        assertEquals("ENCRYPTED_PDF", encryptedError.getCode());

        Path emptyPasswordEncrypted = temporaryDirectory.resolve("empty-password.pdf");
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            document.protect(new StandardProtectionPolicy(
                "owner-password",
                "",
                new AccessPermission()
            ));
            document.save(emptyPasswordEncrypted.toFile());
        }
        OperationException emptyPasswordError = assertThrows(
            OperationException.class,
            () -> operation.execute(context(List.of(
                input(1, emptyPasswordEncrypted, "empty-password.pdf"),
                input(2, redFixture(), "red.pdf")
            ), "{}"))
        );
        assertEquals("ENCRYPTED_PDF", emptyPasswordError.getCode());
    }

    @Test
    void enforcesSubmissionFileTypeAndTotalSizeLimits() {
        OperationException typeError = assertThrows(
            OperationException.class,
            () -> operation.validateSubmission(new OperationSubmission(
                new ObjectMapper().createObjectNode(),
                List.of(
                    new OperationSubmission.UploadDescriptor(1, "one.pdf", "application/pdf", 10),
                    new OperationSubmission.UploadDescriptor(2, "two.txt", "text/plain", 10)
                )
            ))
        );
        assertEquals("INVALID_FILE_TYPE", typeError.getCode());

        OperationException extensionOnlyError = assertThrows(
            OperationException.class,
            () -> operation.validateSubmission(new OperationSubmission(
                new ObjectMapper().createObjectNode(),
                List.of(
                    new OperationSubmission.UploadDescriptor(1, ".pdf", "application/pdf", 1),
                    new OperationSubmission.UploadDescriptor(2, "two.pdf", "application/pdf", 1)
                )
            ))
        );
        assertEquals("INVALID_FILE_TYPE", extensionOnlyError.getCode());

        properties.setMaxTotalInputBytes(10);
        OperationException sizeError = assertThrows(
            OperationException.class,
            () -> operation.validateSubmission(new OperationSubmission(
                new ObjectMapper().createObjectNode(),
                List.of(
                    new OperationSubmission.UploadDescriptor(1, "one.pdf", "application/pdf", 6),
                    new OperationSubmission.UploadDescriptor(2, "two.pdf", "application/pdf", 6)
                )
            ))
        );
        assertEquals("MERGE_INPUT_TOO_LARGE", sizeError.getCode());

        OperationException filenameError = assertThrows(
            OperationException.class,
            () -> operation.validateSubmission(new OperationSubmission(
                new ObjectMapper().createObjectNode().put("outputFilename", "output.txt"),
                List.of(
                    new OperationSubmission.UploadDescriptor(1, "one.pdf", "application/pdf", 1),
                    new OperationSubmission.UploadDescriptor(2, "two.pdf", "application/pdf", 1)
                )
            ))
        );
        assertEquals("INVALID_OUTPUT_FILENAME", filenameError.getCode());
    }

    @Test
    void preservesInheritedPageResourcesAndBoundsDefaultFilename() throws Exception {
        Path inherited = temporaryDirectory.resolve("inherited.pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            BufferedImage blueSquare = new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D graphics = blueSquare.createGraphics();
            graphics.setColor(Color.BLUE);
            graphics.fillRect(0, 0, 20, 20);
            graphics.dispose();
            PDImageXObject image = LosslessFactory.createFromImage(document, blueSquare);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(
                    new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD),
                    18
                );
                content.newLineAtOffset(72, 700);
                content.showText("Inherited resources");
                content.endText();
                content.drawImage(image, 72, 540, 100, 100);
            }

            document.getDocumentCatalog()
                .getPages()
                .getCOSObject()
                .setItem(COSName.RESOURCES, page.getResources());
            page.getCOSObject().removeItem(COSName.RESOURCES);
            document.save(inherited.toFile());
        }
        String longFilename = "文".repeat(45) + ".pdf";

        List<OperationOutput> outputs = operation.execute(context(List.of(
            input(1, inherited, longFilename),
            input(2, redFixture(), "red.pdf")
        ), "{}"));

        assertTrue(
            outputs.getFirst().filename().getBytes(StandardCharsets.UTF_8).length <= 120
        );
        assertTrue(outputs.getFirst().filename().endsWith("_merged.pdf"));
        assertTrue(Files.isDirectory(temporaryDirectory.resolve(".pdfbox-scratch")));
        try (PDDocument merged = Loader.loadPDF(outputs.getFirst().path().toFile())) {
            assertTrue(merged.getPage(0).getResources().getFontNames().iterator().hasNext());
            var imageName = merged.getPage(0)
                .getResources()
                .getXObjectNames()
                .iterator()
                .next();
            PDImageXObject mergedImage = (PDImageXObject) merged.getPage(0)
                .getResources()
                .getXObject(imageName);
            assertEquals(Color.BLUE.getRGB(), mergedImage.getImage().getRGB(10, 10));
            assertTrue(new PDFTextStripper().getText(merged).contains("Inherited resources"));
            new PDFRenderer(merged).renderImage(0);
        }
    }

    @Test
    void fallsBackToAnExtensionBearingNameWhenExecutionBypassesSubmissionValidation()
            throws Exception {
        List<OperationOutput> outputs = operation.execute(context(List.of(
            input(1, redFixture(), ".pdf"),
            input(2, redFixture(), "second.pdf")
        ), "{}"));

        assertEquals("merged.pdf", outputs.getFirst().filename());
    }

    @Test
    void checksCancellationAroundEachSourceAppend() throws Exception {
        Path multiPage = PdfTestFixtures.coloredPdf(
            temporaryDirectory.resolve("multi.pdf"),
            List.of(
                new PdfTestFixtures.PageSpec(100, 100, Color.RED),
                new PdfTestFixtures.PageSpec(100, 100, Color.GREEN)
            )
        );
        Path second = PdfTestFixtures.coloredPdf(
            temporaryDirectory.resolve("second.pdf"),
            List.of(new PdfTestFixtures.PageSpec(100, 100, Color.BLUE))
        );
        AtomicInteger checks = new AtomicInteger();
        OperationContext context = new OperationContext(
            java.util.UUID.randomUUID(),
            new ObjectMapper().createObjectNode(),
            List.of(input(1, multiPage, "multi.pdf"), input(2, second, "second.pdf")),
            temporaryDirectory,
            ignored -> {
            },
            () -> checks.incrementAndGet() > 3
        );

        assertThrows(
            com.pdftools.operations.OperationCancelledException.class,
            () -> operation.execute(context)
        );
    }

    private OperationContext context(List<OperationInput> inputs, String options) throws Exception {
        return new OperationContext(
            java.util.UUID.randomUUID(),
            new ObjectMapper().readTree(options),
            inputs,
            temporaryDirectory,
            ignored -> {
            },
            () -> false
        );
    }

    private OperationInput input(int position, Path path, String filename) throws Exception {
        return new OperationInput(
            position,
            path,
            filename,
            "application/pdf",
            Files.size(path),
            "test-sha"
        );
    }

    private Path redFixture() throws Exception {
        return PdfTestFixtures.coloredPdf(
            temporaryDirectory.resolve("red-fixture-" + java.util.UUID.randomUUID() + ".pdf"),
            List.of(new PdfTestFixtures.PageSpec(100, 100, Color.RED))
        );
    }

    private void assertCenterColor(BufferedImage image, Color expected) {
        Color actual = new Color(image.getRGB(image.getWidth() / 2, image.getHeight() / 2));
        assertTrue(Math.abs(actual.getRed() - expected.getRed()) <= 2);
        assertTrue(Math.abs(actual.getGreen() - expected.getGreen()) <= 2);
        assertTrue(Math.abs(actual.getBlue() - expected.getBlue()) <= 2);
    }
}
