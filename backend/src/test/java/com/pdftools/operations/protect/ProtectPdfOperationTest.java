package com.pdftools.operations.protect;

import com.pdftools.operations.OperationContext;
import com.pdftools.operations.OperationException;
import com.pdftools.operations.OperationInput;
import com.pdftools.operations.OperationOutput;
import com.pdftools.operations.OperationSubmission;
import com.pdftools.operations.security.PdfSecurityProperties;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtectPdfOperationTest {

    @TempDir
    Path temporaryDirectory;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ProtectPdfOperation operation = new ProtectPdfOperation(
        new PdfProtectionEngine(new PdfSecurityProperties()),
        new ProtectPlanFactory()
    );

    @Test
    void encryptsWithAes256AndAppliesUserPermissions() throws Exception {
        OperationOutput output = operation.execute(context(
            sourcePdf(),
            """
            {
              "userPassword":"open-secret",
              "ownerPassword":"owner-secret",
              "permissions":{
                "print":"low",
                "copy":false,
                "modify":false,
                "annotate":true,
                "fillForms":true,
                "accessibility":true,
                "assemble":false
              }
            }
            """
        )).getFirst();

        assertEquals("source_protected.pdf", output.filename());
        assertThrows(
            InvalidPasswordException.class,
            () -> Loader.loadPDF(output.path().toFile())
        );
        try (PDDocument user = Loader.loadPDF(
                output.path().toFile(),
                "open-secret")) {
            assertTrue(user.isEncrypted());
            AccessPermission permission = user.getCurrentAccessPermission();
            assertTrue(permission.canPrint());
            assertFalse(permission.canPrintFaithful());
            assertFalse(permission.canExtractContent());
            assertFalse(permission.canModify());
            assertTrue(permission.canModifyAnnotations());
            assertTrue(permission.canFillInForm());
            assertTrue(permission.canExtractForAccessibility());
            assertFalse(permission.canAssembleDocument());
        }
        try (PDDocument owner = Loader.loadPDF(
                output.path().toFile(),
                "owner-secret")) {
            assertTrue(
                owner.getCurrentAccessPermission().isOwnerPermission()
            );
        }
    }

    @Test
    void rejectsEncryptedInputAndInvalidSecurityOptions() throws Exception {
        Path encrypted = temporaryDirectory.resolve("encrypted.pdf");
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            document.protect(new StandardProtectionPolicy(
                "owner",
                "user",
                new AccessPermission()
            ));
            document.save(encrypted.toFile());
        }
        assertCode("ENCRYPTED_PDF", encrypted, validOptions());
        assertCode(
            "PASSWORDS_MUST_DIFFER",
            sourcePdf(),
            """
            {
              "userPassword":"same",
              "ownerPassword":"same"
            }
            """
        );
        assertCode(
            "INVALID_PERMISSIONS",
            sourcePdf(),
            """
            {
              "userPassword":"user",
              "ownerPassword":"owner",
              "permissions":{"print":"medium"}
            }
            """
        );
        assertCode(
            "INVALID_PASSWORD",
            sourcePdf(),
            """
            {
              "userPassword":"owner",
              "ownerPassword":"ow\u00ADner"
            }
            """
        );
    }

    @Test
    void validatesSubmissionContract() throws Exception {
        OperationSubmission.UploadDescriptor pdf =
            new OperationSubmission.UploadDescriptor(
                1,
                "source.pdf",
                "application/pdf",
                100
            );
        assertEquals(
            "PASSWORD_REQUIRED",
            assertThrows(
                OperationException.class,
                () -> operation.validateSubmission(new OperationSubmission(
                    objectMapper.readTree("{}"),
                    List.of(pdf)
                ))
            ).getCode()
        );
        assertTrue(operation.hasSensitiveOptions());
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

    private String validOptions() {
        return """
            {
              "userPassword":"open-secret",
              "ownerPassword":"owner-secret"
            }
            """;
    }

    private Path sourcePdf() throws Exception {
        Path source = temporaryDirectory.resolve(
            "source-" + UUID.randomUUID() + ".pdf"
        );
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
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
            Files.createTempDirectory(temporaryDirectory, "protect-context-"),
            ignored -> {
            },
            () -> false
        );
    }
}
