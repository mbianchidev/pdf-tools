package com.pdftools.operations.compare;

import com.pdftools.operations.OperationContext;
import com.pdftools.operations.OperationException;
import com.pdftools.operations.OperationInput;
import com.pdftools.operations.OperationOutput;
import com.pdftools.operations.OperationSubmission;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.awt.Color;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComparePdfOperationTest {

    @TempDir
    Path temporaryDirectory;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CompareProperties properties = new CompareProperties();
    private final ComparePlanFactory planFactory =
        new ComparePlanFactory();
    private final ComparePdfOperation operation = new ComparePdfOperation(
        new ComparePdfEngine(properties, planFactory),
        planFactory,
        properties
    );

    @Test
    void reportsTextLayoutAndVisualDifferences() throws Exception {
        Path baseline = pdf(
            "baseline.pdf",
            "Revenue 100",
            50,
            Color.BLUE,
            true,
            false
        );
        Path candidate = pdf(
            "candidate.pdf",
            "Revenue 120",
            80,
            Color.RED,
            true,
            false
        );

        List<OperationOutput> outputs = operation.execute(context(
            baseline,
            candidate,
            """
                {
                  "renderDpi":72,
                  "pixelTolerance":0,
                  "layoutTolerancePoints":1
                }
                """
        ));

        assertEquals(2, outputs.size());
        assertEquals(
            "baseline-vs-candidate-comparison.zip",
            outputs.getFirst().filename()
        );
        JsonNode report = objectMapper.readTree(
            outputs.get(1).path().toFile()
        );
        assertEquals("different", report.path("status").asText());
        assertEquals(
            1,
            report.path("summary").path("textChangedPages").asInt()
        );
        assertEquals(
            1,
            report.path("summary").path("layoutChangedPages").asInt()
        );
        assertEquals(
            1,
            report.path("summary").path("visualChangedPages").asInt()
        );
        JsonNode first = report.path("pages").get(0);
        assertTrue(first.path("text").path("changed").asBoolean());
        assertTrue(first.path("layout").path("changed").asBoolean());
        assertTrue(first.path("layout").path("movedTextLines").asInt() > 0);
        assertTrue(first.path("visual").path("changed").asBoolean());
        assertEquals(
            "visual/page-001-diff.png",
            first.path("visual").path("diffImage").asText()
        );
        assertTrue(report.toString().contains("Revenue 100"));
        assertTrue(report.toString().contains("Revenue 120"));
        JsonNode second = report.path("pages").get(1);
        assertFalse(second.path("text").path("changed").asBoolean());
        assertFalse(second.path("layout").path("changed").asBoolean());
        assertFalse(second.path("visual").path("changed").asBoolean());
        assertTrue(second.path("visual").path("diffImage").isNull());

        try (ZipFile archive =
                 new ZipFile(outputs.getFirst().path().toFile())) {
            assertNotNull(archive.getEntry("comparison-report.json"));
            assertNotNull(archive.getEntry(
                "visual/page-001-diff.png"
            ));
            assertNull(archive.getEntry(
                "visual/page-002-diff.png"
            ));
            assertArrayEquals(
                Files.readAllBytes(outputs.get(1).path()),
                archive.getInputStream(
                    archive.getEntry("comparison-report.json")
                ).readAllBytes()
            );
        }
    }

    @Test
    void reportsIdenticalDocumentsWithoutDiffImages() throws Exception {
        Path source = pdf(
            "same.pdf",
            "Revenue 100",
            50,
            Color.BLUE,
            true,
            false
        );

        List<OperationOutput> outputs = operation.execute(context(
            source,
            source,
            "{\"renderDpi\":72}"
        ));

        JsonNode report = objectMapper.readTree(
            outputs.get(1).path().toFile()
        );
        assertEquals("identical", report.path("status").asText());
        assertEquals(
            0,
            report.path("summary").path("visualChangedPages").asInt()
        );
        try (ZipFile archive =
                 new ZipFile(outputs.getFirst().path().toFile())) {
            assertEquals(1, archive.size());
        }
    }

    @Test
    void comparesMissingPagesAndRejectsEncryptedInputs()
            throws Exception {
        Path baseline = pdf(
            "two-pages.pdf",
            "Revenue 100",
            50,
            Color.BLUE,
            true,
            false
        );
        Path candidate = pdf(
            "one-page.pdf",
            "Revenue 100",
            50,
            Color.BLUE,
            false,
            false
        );

        JsonNode report = objectMapper.readTree(
            operation.execute(context(
                baseline,
                candidate,
                "{\"renderDpi\":72}"
            )).get(1).path().toFile()
        );

        assertEquals(
            2,
            report.path("summary").path("baselinePages").asInt()
        );
        assertEquals(
            1,
            report.path("summary").path("candidatePages").asInt()
        );
        assertFalse(
            report.path("pages").get(1)
                .path("candidatePresent").asBoolean()
        );

        Path encrypted = pdf(
            "encrypted.pdf",
            "Revenue 100",
            50,
            Color.BLUE,
            false,
            true
        );
        OperationException failure = assertThrows(
            OperationException.class,
            () -> operation.execute(context(
                baseline,
                encrypted,
                "{}"
            ))
        );
        assertEquals(
            "ENCRYPTED_PDF_NOT_SUPPORTED",
            failure.getCode()
        );
    }

    @Test
    void validatesOrderedPairSubmission() throws Exception {
        operation.validateSubmission(new OperationSubmission(
            objectMapper.readTree("{}"),
            List.of(
                descriptor("baseline.pdf", 100),
                descriptor("candidate.pdf", 100)
            )
        ));
        OperationException count = assertThrows(
            OperationException.class,
            () -> operation.validateSubmission(new OperationSubmission(
                objectMapper.readTree("{}"),
                List.of(descriptor("baseline.pdf", 100))
            ))
        );
        assertEquals("INVALID_FILE_COUNT", count.getCode());
    }

    private Path pdf(
            String filename,
            String revenue,
            float titleX,
            Color rectangle,
            boolean secondPage,
            boolean encrypted) throws Exception {
        Path source = temporaryDirectory.resolve(filename);
        try (PDDocument document = new PDDocument()) {
            PDPage first = new PDPage();
            document.addPage(first);
            try (PDPageContentStream stream =
                     new PDPageContentStream(document, first)) {
                write(stream, titleX, 730, "Quarterly Report");
                write(stream, 50, 690, revenue);
                stream.setNonStrokingColor(rectangle);
                stream.addRect(50, 500, 100, 50);
                stream.fill();
            }
            if (secondPage) {
                PDPage second = new PDPage();
                document.addPage(second);
                try (PDPageContentStream stream =
                         new PDPageContentStream(document, second)) {
                    write(stream, 50, 730, "UNCHANGED PAGE");
                }
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

    private void write(
            PDPageContentStream stream,
            float x,
            float y,
            String text) throws Exception {
        stream.beginText();
        stream.setFont(
            new PDType1Font(Standard14Fonts.FontName.HELVETICA),
            12
        );
        stream.newLineAtOffset(x, y);
        stream.showText(text);
        stream.endText();
    }

    private OperationContext context(
            Path baseline,
            Path candidate,
            String options) throws Exception {
        return new OperationContext(
            UUID.randomUUID(),
            objectMapper.readTree(options),
            List.of(
                input(1, baseline, "baseline.pdf"),
                input(2, candidate, "candidate.pdf")
            ),
            Files.createTempDirectory(
                temporaryDirectory,
                "compare-context-"
            ),
            ignored -> {
            },
            () -> false
        );
    }

    private OperationInput input(
            int position,
            Path source,
            String filename) throws Exception {
        return new OperationInput(
            position,
            source,
            filename,
            "application/pdf",
            Files.size(source),
            sha256(source)
        );
    }

    private String sha256(Path source) throws Exception {
        return HexFormat.of().formatHex(
            MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(source))
        );
    }

    private OperationSubmission.UploadDescriptor descriptor(
            String filename,
            long size) {
        return new OperationSubmission.UploadDescriptor(
            1,
            filename,
            "application/pdf",
            size
        );
    }
}
