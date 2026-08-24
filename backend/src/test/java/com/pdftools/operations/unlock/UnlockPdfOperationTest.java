package com.pdftools.operations.unlock;

import com.pdftools.operations.OperationContext;
import com.pdftools.operations.OperationException;
import com.pdftools.operations.OperationInput;
import com.pdftools.operations.OperationOutput;
import com.pdftools.operations.OperationSubmission;
import com.pdftools.operations.security.PdfSecurityProperties;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnlockPdfOperationTest {

    @TempDir
    Path temporaryDirectory;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PdfSecurityProperties securityProperties =
        new PdfSecurityProperties();
    private final UnlockPdfOperation operation = new UnlockPdfOperation(
        new PdfUnlockEngine(securityProperties),
        new UnlockPlanFactory()
    );

    @Test
    void unlocksWithUserOrOwnerPasswordAndPreservesContent() throws Exception {
        Path encrypted = encryptedPdf(
            "p\u00E4ssword",
            "owner-secret",
            256,
            true
        );

        OperationOutput userOutput = operation.execute(context(
            encrypted,
            """
            {"password":"p\\u00E4ssword"}
            """
        )).getFirst();
        assertEquals("source_unlocked.pdf", userOutput.filename());
        assertUnlockedContent(userOutput.path());

        OperationOutput ownerOutput = operation.execute(context(
            encrypted,
            """
            {
              "password":"owner-secret",
              "outputFilename":"owner-copy.pdf"
            }
            """
        )).getFirst();
        assertEquals("owner-copy.pdf", ownerOutput.filename());
        assertUnlockedContent(ownerOutput.path());
    }

    @ParameterizedTest
    @CsvSource({
        "40, false",
        "128, true",
        "256, true"
    })
    void unlocksStandardPasswordEncryption(
            int keyLength,
            boolean preferAes) throws Exception {
        Path encrypted = encryptedPdf(
            "open-secret",
            "owner-secret",
            keyLength,
            preferAes
        );

        OperationOutput output = operation.execute(context(
            encrypted,
            """
            {"password":"open-secret"}
            """
        )).getFirst();

        assertUnlockedContent(output.path());
    }

    @Test
    void rejectsWrongPasswordAndUnencryptedInput() throws Exception {
        assertCode(
            "INVALID_PASSWORD",
            encryptedPdf("open-secret", "owner-secret", 256, true),
            """
            {"password":"wrong-secret"}
            """
        );
        assertCode(
            "PDF_NOT_ENCRYPTED",
            plainPdf(),
            """
            {"password":"open-secret"}
            """
        );
        assertCode(
            "INVALID_PASSWORD",
            encryptedPdf("open-secret", "owner-secret", 256, true),
            """
            {"password":"\\u200E"}
            """
        );
    }

    @Test
    void validatesSensitiveSubmissionContract() throws Exception {
        OperationSubmission.UploadDescriptor pdf =
            new OperationSubmission.UploadDescriptor(
                1,
                "source.pdf",
                "application/pdf",
                100
            );
        assertSubmissionCode("PASSWORD_REQUIRED", "{}", pdf);
        assertSubmissionCode(
            "INVALID_PASSWORD",
            """
            {"password":42}
            """,
            pdf
        );
        assertSubmissionCode(
            "PASSWORD_TOO_LONG",
            """
            {"password":"%s"}
            """.formatted("x".repeat(128)),
            pdf
        );
        assertTrue(operation.hasSensitiveOptions());
    }

    @Test
    void removesPartialOutputWhenSizeLimitIsExceeded() throws Exception {
        securityProperties.setMaxOutputBytes(100);
        OperationException exception = assertThrows(
            OperationException.class,
            () -> operation.execute(context(
                encryptedPdf(
                    "open-secret",
                    "owner-secret",
                    256,
                    true
                ),
                """
                {"password":"open-secret"}
                """
            ))
        );

        assertEquals(
            "UNLOCKED_OUTPUT_SIZE_LIMIT_EXCEEDED",
            exception.getCode()
        );
    }

    private void assertUnlockedContent(Path output) throws Exception {
        try (PDDocument document = Loader.loadPDF(output.toFile())) {
            assertFalse(document.isEncrypted());
            assertTrue(
                document.getCurrentAccessPermission().isOwnerPermission()
            );
            assertEquals("Unlock fixture", document.getDocumentInformation()
                .getTitle());
            assertTrue(new PDFTextStripper().getText(document)
                .contains("Preserved content"));
        }
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

    private Path encryptedPdf(
            String userPassword,
            String ownerPassword,
            int keyLength,
            boolean preferAes)
            throws Exception {
        Path source = plainPdf();
        try (PDDocument document = Loader.loadPDF(source.toFile())) {
            StandardProtectionPolicy policy = new StandardProtectionPolicy(
                ownerPassword,
                userPassword,
                new AccessPermission()
            );
            policy.setEncryptionKeyLength(keyLength);
            policy.setPreferAES(preferAes);
            document.protect(policy);
            Path encrypted = temporaryDirectory.resolve(
                "encrypted-" + UUID.randomUUID() + ".pdf"
            );
            document.save(encrypted.toFile());
            return encrypted;
        }
    }

    private Path plainPdf() throws Exception {
        Path source = temporaryDirectory.resolve(
            "source-" + UUID.randomUUID() + ".pdf"
        );
        try (PDDocument document = new PDDocument()) {
            document.getDocumentInformation().setTitle("Unlock fixture");
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content =
                    new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(
                    new PDType1Font(Standard14Fonts.FontName.HELVETICA),
                    12
                );
                content.newLineAtOffset(72, 700);
                content.showText("Preserved content");
                content.endText();
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
                "test-sha"
            )),
            Files.createTempDirectory(temporaryDirectory, "unlock-context-"),
            ignored -> {
            },
            () -> false
        );
    }
}
