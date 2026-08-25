package com.pdftools.operations.repair;

import com.pdftools.operations.OperationContext;
import com.pdftools.operations.OperationException;
import com.pdftools.operations.OperationInput;
import com.pdftools.operations.OperationOutput;
import com.pdftools.operations.OperationSubmission;
import org.apache.pdfbox.Loader;
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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepairPdfOperationTest {

    private static final Pattern START_XREF = Pattern.compile(
        "startxref\\s+(\\d+)"
    );

    @TempDir
    Path temporaryDirectory;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RepairPdfProperties properties =
        new RepairPdfProperties();
    private final RepairPdfOperation operation = new RepairPdfOperation(
        new RepairPdfEngine(properties),
        properties
    );

    @Test
    void repairsDamagedCrossReferenceWithExplicitWarnings()
            throws Exception {
        Path damaged = damageStartXref(textPdf(null));

        List<OperationOutput> outputs = operation.execute(context(damaged));

        assertEquals(2, outputs.size());
        assertEquals("report-repaired.pdf", outputs.getFirst().filename());
        assertEquals(
            "report-repair-report.json",
            outputs.get(1).filename()
        );
        try (PDDocument repaired =
                 Loader.loadPDF(outputs.getFirst().path().toFile())) {
            assertEquals(1, repaired.getNumberOfPages());
        }
        JsonNode report = objectMapper.readTree(outputs.get(1).path());
        assertEquals(
            "partially-recovered",
            report.path("status").asText()
        );
        assertTrue(report.path("warnings").size() > 0);
        assertTrue(
            report.path("warnings").toString()
                .contains("cross-reference")
        );
        assertFalse(
            report.path("warnings").toString()
                .contains(temporaryDirectory.toString())
        );
    }

    @Test
    void cleanRewriteIsDeterministic() throws Exception {
        Path source = textPdf(null);

        List<OperationOutput> first = operation.execute(context(source));
        List<OperationOutput> second = operation.execute(context(source));

        assertArrayEquals(
            Files.readAllBytes(first.getFirst().path()),
            Files.readAllBytes(second.getFirst().path())
        );
        JsonNode report = objectMapper.readTree(first.get(1).path());
        assertEquals("repaired", report.path("status").asText());
        assertEquals(0, report.path("warnings").size());
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

        OperationException encrypted = assertThrows(
            OperationException.class,
            () -> operation.execute(context(textPdf("user")))
        );
        assertEquals(
            "ENCRYPTED_PDF_NOT_SUPPORTED",
            encrypted.getCode()
        );
        OperationException ownerOnly = assertThrows(
            OperationException.class,
            () -> operation.execute(context(textPdf("")))
        );
        assertEquals(
            "ENCRYPTED_PDF_NOT_SUPPORTED",
            ownerOnly.getCode()
        );
    }

    private Path textPdf(String userPassword) throws Exception {
        Path source = temporaryDirectory.resolve(
            userPassword == null
                ? UUID.randomUUID() + ".pdf"
                : "encrypted-" + UUID.randomUUID() + ".pdf"
        );
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream stream =
                     new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(
                    new PDType1Font(
                        Standard14Fonts.FontName.HELVETICA
                    ),
                    12
                );
                stream.newLineAtOffset(50, 700);
                stream.showText("REPAIRABLE");
                stream.endText();
            }
            if (userPassword != null) {
                document.protect(new StandardProtectionPolicy(
                    "owner",
                    userPassword,
                    new AccessPermission()
                ));
            }
            document.save(source.toFile());
        }
        return source;
    }

    private Path damageStartXref(Path source) throws Exception {
        String pdf = Files.readString(
            source,
            StandardCharsets.ISO_8859_1
        );
        Matcher matcher = START_XREF.matcher(pdf);
        assertTrue(matcher.find());
        String replacement = "startxref\n"
            + "0".repeat(matcher.group(1).length());
        Path damaged = temporaryDirectory.resolve("damaged.pdf");
        Files.writeString(
            damaged,
            matcher.replaceFirst(replacement),
            StandardCharsets.ISO_8859_1
        );
        return damaged;
    }

    private OperationContext context(Path source) throws Exception {
        return new OperationContext(
            UUID.randomUUID(),
            objectMapper.readTree("{}"),
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
                "repair-context-"
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
