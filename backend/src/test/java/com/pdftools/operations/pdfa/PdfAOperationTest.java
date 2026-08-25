package com.pdftools.operations.pdfa;

import com.pdftools.operations.OperationContext;
import com.pdftools.operations.OperationException;
import com.pdftools.operations.OperationInput;
import com.pdftools.operations.OperationOutput;
import com.pdftools.operations.OperationSubmission;
import com.pdftools.operations.office.LibreOfficeConverter;
import com.pdftools.operations.office.NativeProcessSandbox;
import com.pdftools.operations.office.OfficeConversionProperties;
import com.pdftools.operations.office.OfficeConversionQueueClient;
import com.pdftools.operations.shared.worker.IsolatedJavaWorker;
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfAOperationTest {

    @TempDir
    Path temporaryDirectory;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PdfAProperties properties = new PdfAProperties();
    private final PdfAPlanFactory planFactory = new PdfAPlanFactory();
    private final PdfADocumentValidator documentValidator =
        new PdfADocumentValidator(properties);
    private final OfficeConversionProperties officeProperties =
        officeProperties();
    private final PdfAOperation operation = new PdfAOperation(
        new PdfAEngine(
            properties,
            documentValidator,
            planFactory,
            new PdfAConverter(
                officeProperties,
                new LibreOfficePdfAConverter(
                    planFactory,
                    new LibreOfficeConverter(
                        officeProperties,
                        new NativeProcessSandbox()
                    )
                ),
                new OfficeConversionQueueClient(officeProperties)
            )
        ),
        documentValidator,
        planFactory
    );

    @Test
    void convertsAndValidatesEverySupportedProfile() throws Exception {
        Path source = textPdf(null);
        for (String profile : List.of(
                "pdfa-1b",
                "pdfa-2b",
                "pdfa-3b")) {
            List<OperationOutput> outputs = operation.execute(context(
                source,
                "{\"profile\":\"" + profile + "\"}"
            ));

            assertEquals(2, outputs.size());
            assertEquals(
                "report-" + profile + ".pdf",
                outputs.getFirst().filename()
            );
            assertEquals(
                "report-" + profile + "-validation-report.json",
                outputs.get(1).filename()
            );
            JsonNode report = objectMapper.readTree(
                outputs.get(1).path().toFile()
            );
            assertEquals("compliant", report.path("status").asText());
            assertEquals(profile, report.path("profile").asText());
            assertTrue(report.path("compliant").asBoolean());
            assertTrue(report.path("totalAssertions").asInt() > 0);
            assertEquals(0, report.path("failedChecks").asLong());
        }
    }

    @Test
    void veraPdfRejectsAnOrdinaryPdfForPdfA2b() throws Exception {
        Path source = textPdf(null);
        Path report = temporaryDirectory.resolve("invalid-report.json");
        Path request = temporaryDirectory.resolve("validation-request.bin");
        Path error = temporaryDirectory.resolve("validation-error");
        PdfAValidationRequest.write(
            request,
            source,
            report,
            PdfAPlanFactory.PdfAProfile.PDFA_2_B,
            properties
        );

        int exitCode = IsolatedJavaWorker.run(
            new IsolatedJavaWorker.Spec(
                PdfAValidationWorkerMain.class,
                properties.getValidatorHeapBytes(),
                properties.getValidatorTimeout(),
                "START_FAILED",
                "start failed",
                "TIMEOUT",
                "timeout",
                PdfAEngine.validatorJvmArguments()
            ),
            List.of(request.toString(), error.toString()),
            () -> {
            },
            () -> {
            }
        );

        assertEquals(2, exitCode);
        assertEquals(
            "PDFA_VALIDATION_FAILED",
            Files.readAllLines(error).getFirst()
        );
        JsonNode result = objectMapper.readTree(report.toFile());
        assertEquals("non-compliant", result.path("status").asText());
        assertTrue(result.path("failedChecks").asLong() > 0);
    }

    @Test
    void validatesSubmissionAndRejectsEncryptedPdf() throws Exception {
        operation.validateSubmission(new OperationSubmission(
            objectMapper.readTree("{\"profile\":\"pdfa-2b\"}"),
            List.of(descriptor("report.pdf", "application/pdf", 100))
        ));
        OperationException invalidProfile = assertThrows(
            OperationException.class,
            () -> operation.validateSubmission(new OperationSubmission(
                objectMapper.readTree("{\"profile\":\"pdfa-2a\"}"),
                List.of(descriptor("report.pdf", "application/pdf", 100))
            ))
        );
        assertEquals(
            "INVALID_PDFA_PROFILE",
            invalidProfile.getCode()
        );

        OperationException encrypted = assertThrows(
            OperationException.class,
            () -> operation.execute(context(
                textPdf("user"),
                "{\"profile\":\"pdfa-2b\"}"
            ))
        );
        assertEquals(
            "ENCRYPTED_PDF_NOT_SUPPORTED",
            encrypted.getCode()
        );
    }

    private OfficeConversionProperties officeProperties() {
        OfficeConversionProperties configured =
            new OfficeConversionProperties();
        configured.setMode("direct");
        configured.setIsolatedContainer(true);
        configured.setWorkerUser(System.getProperty("user.name"));
        configured.setMaxWorkerProcesses(4096);
        configured.setLibreOfficeBinary(localSoffice());
        configured.setWallTimeout(Duration.ofMinutes(1));
        configured.setCpuTimeSeconds(60);
        return configured;
    }

    private String localSoffice() {
        for (String candidate : List.of(
                "/opt/homebrew/bin/soffice",
                "/Applications/LibreOffice.app/Contents/MacOS/soffice",
                "/usr/bin/soffice")) {
            if (Files.isExecutable(Path.of(candidate))) {
                return candidate;
            }
        }
        return "soffice";
    }

    private Path textPdf(String userPassword) throws Exception {
        Path source = temporaryDirectory.resolve(
            UUID.randomUUID() + ".pdf"
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
                stream.showText("ARCHIVAL PDF");
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
                "pdfa-context-"
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
