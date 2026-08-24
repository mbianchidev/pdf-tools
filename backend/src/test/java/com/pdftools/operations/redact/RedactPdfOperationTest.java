package com.pdftools.operations.redact;

import com.pdftools.operations.OperationContext;
import com.pdftools.operations.OperationException;
import com.pdftools.operations.OperationInput;
import com.pdftools.operations.OperationOutput;
import com.pdftools.operations.OperationSubmission;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDDocumentNameDictionary;
import org.apache.pdfbox.pdmodel.PDEmbeddedFilesNameTreeNode;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification;
import org.apache.pdfbox.pdmodel.common.filespecification.PDEmbeddedFile;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationText;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedactPdfOperationTest {

    private static final String SECRET_TEXT = "TOP_SECRET_VALUE";
    private static final String REVISION_SECRET = "REVISION_SECRET_VALUE";
    private static final String ATTACHMENT_SECRET = "ATTACHMENT_SECRET_VALUE";

    @TempDir
    Path temporaryDirectory;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RedactProperties properties = properties();
    private final RedactPlanFactory planFactory =
        new RedactPlanFactory(properties);
    private final RedactPdfOperation operation = new RedactPdfOperation(
        new RedactEngine(planFactory, properties),
        planFactory
    );

    @Test
    void rasterizesDocumentAndRemovesRecoverableContent() throws Exception {
        Path source = sensitivePdf();

        OperationOutput output = operation.execute(context(
            source,
            """
            {
              "areas":[
                {"page":1,"x":0.1,"y":0.2,"width":0.4,"height":0.4}
              ]
            }
            """
        )).getFirst();

        assertEquals("source_redacted.pdf", output.filename());
        assertEquals("application/pdf", output.mediaType());
        String rawOutput = Files.readString(
            output.path(),
            StandardCharsets.ISO_8859_1
        );
        assertFalse(rawOutput.contains(SECRET_TEXT));
        assertFalse(rawOutput.contains(ATTACHMENT_SECRET));

        try (PDDocument redacted = Loader.loadPDF(output.path().toFile())) {
            assertEquals(2, redacted.getNumberOfPages());
            assertTrue(new PDFTextStripper().getText(redacted).isBlank());
            assertTrue(redacted.getPage(0).getAnnotations().isEmpty());
            assertTrue(redacted.getPage(1).getAnnotations().isEmpty());
            assertNull(redacted.getDocumentCatalog().getNames());
            assertNull(redacted.getDocumentInformation().getAuthor());

            for (PDPage page : redacted.getPages()) {
                assertFalse(page.getResources().getFontNames().iterator()
                    .hasNext());
                int imageCount = 0;
                for (COSName name : page.getResources().getXObjectNames()) {
                    assertInstanceOf(
                        PDImageXObject.class,
                        page.getResources().getXObject(name)
                    );
                    imageCount++;
                }
                assertEquals(1, imageCount);
            }

            BufferedImage rendered = new PDFRenderer(redacted).renderImage(
                0,
                2
            );
            try {
                assertNearBlack(rendered, 80, 60);
                Color outside = new Color(rendered.getRGB(340, 160));
                assertTrue(outside.getRed() > 220);
                assertTrue(outside.getGreen() > 220);
                assertTrue(outside.getBlue() > 220);
            } finally {
                rendered.flush();
            }
        }
    }

    @Test
    void discardsPriorIncrementalRevisions() throws Exception {
        Path source = incrementalPdf();
        assertTrue(Files.readString(
            source,
            StandardCharsets.ISO_8859_1
        ).contains(REVISION_SECRET));

        OperationOutput output = operation.execute(context(
            source,
            """
            {
              "areas":[
                {"page":1,"x":0,"y":0,"width":1,"height":1}
              ]
            }
            """
        )).getFirst();

        String outputBytes = Files.readString(
            output.path(),
            StandardCharsets.ISO_8859_1
        );
        assertFalse(outputBytes.contains(REVISION_SECRET));
        assertEquals(1, occurrences(outputBytes, "%%EOF"));
    }

    @Test
    void alignsNormalizedAreasWithRotatedCropAndUserUnit() throws Exception {
        OperationOutput output = operation.execute(context(
            rotatedCroppedPdf(),
            """
            {
              "areas":[
                {"page":1,"x":0.2,"y":0.25,"width":0.3,"height":0.25}
              ]
            }
            """
        )).getFirst();

        try (PDDocument redacted = Loader.loadPDF(output.path().toFile())) {
            PDRectangle box = redacted.getPage(0).getMediaBox();
            assertEquals(100, box.getWidth(), 0.01);
            assertEquals(200, box.getHeight(), 0.01);
            assertEquals(0, redacted.getPage(0).getRotation());

            BufferedImage rendered = new PDFRenderer(redacted).renderImage(0);
            try {
                assertNearBlack(rendered, 30, 60);
                Color outside = new Color(rendered.getRGB(80, 160));
                assertTrue(outside.getGreen() > 100);
                assertTrue(outside.getGreen() > outside.getRed() * 2);
            } finally {
                rendered.flush();
            }
        }
    }

    @Test
    void validatesAreasFilesPagesAndResourceLimits() throws Exception {
        OperationSubmission.UploadDescriptor pdf =
            new OperationSubmission.UploadDescriptor(
                1,
                "source.pdf",
                "application/pdf",
                100
            );
        assertSubmissionCode("REDACT_AREAS_REQUIRED", """
            {"areas":[]}
            """, pdf);
        assertSubmissionCode("INVALID_REDACTION_AREA", """
            {
              "areas":[
                {"page":1,"x":0.8,"y":0.2,"width":0.3,"height":0.2}
              ]
            }
            """, pdf);
        assertSubmissionCode("DUPLICATE_REDACTION_AREA", """
            {
              "areas":[
                {"page":1,"x":0.1,"y":0.1,"width":0.2,"height":0.2},
                {"page":1,"x":0.1,"y":0.1,"width":0.2,"height":0.2}
              ]
            }
            """, pdf);
        assertCode("REDACT_PAGE_OUT_OF_RANGE", sensitivePdf(), """
            {
              "areas":[
                {"page":3,"x":0.1,"y":0.1,"width":0.2,"height":0.2}
              ]
            }
            """);

        RedactProperties constrained = properties();
        constrained.setMaxPixelsPerPage(100);
        RedactPlanFactory constrainedPlans =
            new RedactPlanFactory(constrained);
        RedactPdfOperation constrainedOperation = new RedactPdfOperation(
            new RedactEngine(constrainedPlans, constrained),
            constrainedPlans
        );
        OperationException sizeFailure = assertThrows(
            OperationException.class,
            () -> constrainedOperation.execute(context(
                sensitivePdf(),
                """
                {
                  "areas":[
                    {"page":1,"x":0.1,"y":0.1,"width":0.2,"height":0.2}
                  ]
                }
                """
            ))
        );
        assertEquals(
            "REDACT_RENDER_SIZE_LIMIT_EXCEEDED",
            sizeFailure.getCode()
        );
    }

    @Test
    void createsByteDeterministicSanitizedOutputs() throws Exception {
        Path source = sensitivePdf();
        String options = """
            {
              "areas":[
                {"page":1,"x":0.1,"y":0.2,"width":0.4,"height":0.4}
              ]
            }
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
    void terminatesTimedOutWorkerAndRemovesPartialOutput() throws Exception {
        RedactProperties timed = properties();
        timed.setWorkerTimeout(Duration.ofNanos(1));
        RedactPlanFactory timedPlans = new RedactPlanFactory(timed);
        RedactPdfOperation timedOperation = new RedactPdfOperation(
            new RedactEngine(timedPlans, timed),
            timedPlans
        );
        OperationContext context = context(sensitivePdf(), """
            {
              "areas":[
                {"page":1,"x":0.1,"y":0.1,"width":0.2,"height":0.2}
              ]
            }
            """);

        OperationException failure = assertThrows(
            OperationException.class,
            () -> timedOperation.execute(context)
        );

        assertEquals("REDACT_TIMEOUT", failure.getCode());
        assertFalse(Files.exists(context.workspace().resolve("redacted.pdf")));
    }

    private RedactProperties properties() {
        RedactProperties configured = new RedactProperties();
        configured.setRenderDpi(144);
        configured.setJpegQuality(100);
        return configured;
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

    private void assertCode(String code, Path source, String options)
            throws Exception {
        OperationException exception = assertThrows(
            OperationException.class,
            () -> operation.execute(context(source, options))
        );
        assertEquals(code, exception.getCode());
    }

    private Path sensitivePdf() throws Exception {
        Path source = temporaryDirectory.resolve(
            "sensitive-" + UUID.randomUUID() + ".pdf"
        );
        try (PDDocument document = new PDDocument()) {
            PDDocumentInformation information = new PDDocumentInformation();
            information.setAuthor(ATTACHMENT_SECRET);
            document.setDocumentInformation(information);
            addSensitivePage(document);
            document.addPage(new PDPage(new PDRectangle(200, 100)));
            addAttachment(document);
            document.save(source.toFile());
        }
        return source;
    }

    private void addSensitivePage(PDDocument document) throws Exception {
        PDPage page = new PDPage(new PDRectangle(200, 100));
        document.addPage(page);
        BufferedImage secretImage = new BufferedImage(
            80,
            40,
            BufferedImage.TYPE_INT_RGB
        );
        var graphics = secretImage.createGraphics();
        try {
            graphics.setColor(Color.RED);
            graphics.fillRect(0, 0, 80, 40);
        } finally {
            graphics.dispose();
        }
        try (PDPageContentStream content = new PDPageContentStream(
                document,
                page,
                PDPageContentStream.AppendMode.OVERWRITE,
                false)) {
            content.setNonStrokingColor(Color.WHITE);
            content.addRect(0, 0, 200, 100);
            content.fill();
            content.drawImage(
                LosslessFactory.createFromImage(document, secretImage),
                20,
                40,
                80,
                40
            );
            content.beginText();
            content.setFont(
                new PDType1Font(Standard14Fonts.FontName.HELVETICA),
                12
            );
            content.newLineAtOffset(25, 65);
            content.showText(SECRET_TEXT);
            content.endText();
        } finally {
            secretImage.flush();
        }
        PDAnnotationText annotation = new PDAnnotationText();
        annotation.setRectangle(new PDRectangle(120, 60, 20, 20));
        annotation.setContents(ATTACHMENT_SECRET);
        page.getAnnotations().add(annotation);
    }

    private void addAttachment(PDDocument document) throws Exception {
        PDEmbeddedFile embedded = new PDEmbeddedFile(
            document,
            new ByteArrayInputStream(
                ATTACHMENT_SECRET.getBytes(StandardCharsets.US_ASCII)
            )
        );
        embedded.setSubtype("text/plain");
        PDComplexFileSpecification specification =
            new PDComplexFileSpecification();
        specification.setFile("secret.txt");
        specification.setEmbeddedFile(embedded);
        PDEmbeddedFilesNameTreeNode embeddedFiles =
            new PDEmbeddedFilesNameTreeNode();
        embeddedFiles.setNames(Map.of("secret.txt", specification));
        PDDocumentNameDictionary names = new PDDocumentNameDictionary(
            document.getDocumentCatalog()
        );
        names.setEmbeddedFiles(embeddedFiles);
        document.getDocumentCatalog().setNames(names);
    }

    private Path incrementalPdf() throws Exception {
        Path base = temporaryDirectory.resolve("base-revision.pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(new PDRectangle(200, 100));
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(
                    document,
                    page,
                    PDPageContentStream.AppendMode.OVERWRITE,
                    false)) {
                content.beginText();
                content.setFont(
                    new PDType1Font(Standard14Fonts.FontName.HELVETICA),
                    12
                );
                content.newLineAtOffset(30, 50);
                content.showText(REVISION_SECRET);
                content.endText();
            }
            document.save(base.toFile());
        }

        Path incremental = temporaryDirectory.resolve("incremental.pdf");
        try (PDDocument document = Loader.loadPDF(base.toFile());
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            try (PDPageContentStream content = new PDPageContentStream(
                    document,
                    document.getPage(0),
                    PDPageContentStream.AppendMode.APPEND,
                    false,
                    true)) {
                content.setNonStrokingColor(Color.WHITE);
                content.addRect(0, 0, 200, 100);
                content.fill();
            }
            document.saveIncremental(output);
            Files.write(incremental, output.toByteArray());
        }
        return incremental;
    }

    private Path rotatedCroppedPdf() throws Exception {
        Path source = temporaryDirectory.resolve("rotated-cropped.pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(new PDRectangle(140, 100));
            page.setCropBox(new PDRectangle(10, 20, 100, 50));
            page.setRotation(90);
            page.setUserUnit(2);
            document.addPage(page);
            try (PDPageContentStream content =
                    new PDPageContentStream(document, page)) {
                content.setNonStrokingColor(Color.GREEN);
                content.addRect(0, 0, 140, 100);
                content.fill();
            }
            document.save(source.toFile());
        }
        return source;
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
                "test-source-sha"
            )),
            Files.createTempDirectory(
                temporaryDirectory,
                "redact-context-"
            ),
            ignored -> {
            },
            () -> false
        );
    }

    private void assertNearBlack(BufferedImage image, int x, int y) {
        Color color = new Color(image.getRGB(x, y));
        assertTrue(color.getRed() < 30);
        assertTrue(color.getGreen() < 30);
        assertTrue(color.getBlue() < 30);
    }

    private int occurrences(String value, String needle) {
        int count = 0;
        int offset = 0;
        while ((offset = value.indexOf(needle, offset)) >= 0) {
            count++;
            offset += needle.length();
        }
        return count;
    }
}
