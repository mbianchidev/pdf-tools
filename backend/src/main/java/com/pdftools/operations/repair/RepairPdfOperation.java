package com.pdftools.operations.repair;

import com.pdftools.operations.OperationContext;
import com.pdftools.operations.OperationException;
import com.pdftools.operations.OperationOutput;
import com.pdftools.operations.OperationSubmission;
import com.pdftools.operations.PdfOperation;
import com.pdftools.operations.PdfOperationValidation;
import com.pdftools.util.FilenameSanitizer;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class RepairPdfOperation implements PdfOperation {

    private static final int MAX_OUTPUT_FILENAME_BYTES = 120;
    private static final Set<String> MEDIA_TYPES = Set.of(
        "application/pdf",
        "application/octet-stream"
    );

    private final RepairPdfEngine engine;
    private final RepairPdfProperties properties;

    public RepairPdfOperation(
            RepairPdfEngine engine,
            RepairPdfProperties properties) {
        this.engine = engine;
        this.properties = properties;
    }

    @Override
    public String key() {
        return "repair";
    }

    @Override
    public void validateSubmission(OperationSubmission submission) {
        if (submission.files().size() != 1) {
            throw new OperationException(
                "INVALID_FILE_COUNT",
                "Repair PDF requires exactly one PDF"
            );
        }
        OperationSubmission.UploadDescriptor file =
            submission.files().getFirst();
        if (!file.filename().toLowerCase(Locale.ROOT).endsWith(".pdf")
                || !MEDIA_TYPES.contains(
                    file.mediaType().toLowerCase(Locale.ROOT))) {
            throw new OperationException(
                "INVALID_PDF_FILE",
                "Repair PDF accepts one PDF file"
            );
        }
        if (file.sizeBytes() < 1
                || file.sizeBytes() > properties.getMaxInputBytes()) {
            throw new OperationException(
                "REPAIR_INPUT_LIMIT_EXCEEDED",
                "The PDF exceeds the configured repair input limit"
            );
        }
        PdfOperationValidation.validateOptionalOutputFilename(
            submission.options(),
            ".pdf",
            MAX_OUTPUT_FILENAME_BYTES,
            "Repair PDF"
        );
    }

    @Override
    public List<OperationOutput> execute(OperationContext context) {
        var input = context.inputs().getFirst();
        RepairPdfEngine.RepairResult result = engine.repair(
            input.path(),
            context.workspace(),
            context::reportProgress,
            context::checkCancelled
        );
        String pdfFilename = outputFilename(
            context.options(),
            input.originalFilename()
        );
        return List.of(
            new OperationOutput(
                result.pdf(),
                pdfFilename,
                "application/pdf"
            ),
            new OperationOutput(
                result.report(),
                reportFilename(pdfFilename),
                "application/json"
            )
        );
    }

    private String outputFilename(
            JsonNode options,
            String sourceFilename) {
        String requested = options.path("outputFilename").asText("").trim();
        if (!requested.isEmpty()) {
            PdfOperationValidation.validateOutputFilename(
                requested,
                ".pdf",
                MAX_OUTPUT_FILENAME_BYTES,
                "Repair PDF"
            );
            return FilenameSanitizer.sanitize(
                requested,
                "repaired.pdf"
            );
        }
        String sanitized = FilenameSanitizer.sanitize(
            sourceFilename,
            "document.pdf"
        );
        String base = sanitized.toLowerCase(Locale.ROOT).endsWith(".pdf")
            ? sanitized.substring(0, sanitized.length() - 4)
            : sanitized;
        return FilenameSanitizer.sanitize(
            base + "-repaired.pdf",
            "repaired.pdf"
        );
    }

    private String reportFilename(String pdfFilename) {
        String base = pdfFilename.toLowerCase(Locale.ROOT).endsWith(".pdf")
            ? pdfFilename.substring(0, pdfFilename.length() - 4)
            : pdfFilename;
        if (base.endsWith("-repaired")) {
            base = base.substring(0, base.length() - 9);
        }
        return FilenameSanitizer.sanitize(
            base + "-repair-report.json",
            "repair-report.json"
        );
    }
}
