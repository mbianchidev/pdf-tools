package com.pdftools.operations.pdfa;

import com.pdftools.operations.OperationContext;
import com.pdftools.operations.OperationOutput;
import com.pdftools.operations.OperationSubmission;
import com.pdftools.operations.PdfOperation;
import com.pdftools.operations.PdfOperationValidation;
import com.pdftools.util.FilenameSanitizer;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Locale;

@Component
public class PdfAOperation implements PdfOperation {

    private static final int MAX_OUTPUT_FILENAME_BYTES = 120;

    private final PdfAEngine engine;
    private final PdfADocumentValidator documentValidator;
    private final PdfAPlanFactory planFactory;

    public PdfAOperation(
            PdfAEngine engine,
            PdfADocumentValidator documentValidator,
            PdfAPlanFactory planFactory) {
        this.engine = engine;
        this.documentValidator = documentValidator;
        this.planFactory = planFactory;
    }

    @Override
    public String key() {
        return "pdf-to-pdfa";
    }

    @Override
    public void validateSubmission(OperationSubmission submission) {
        documentValidator.validateSubmission(submission);
        planFactory.create(submission.options());
        PdfOperationValidation.validateOptionalOutputFilename(
            submission.options(),
            ".pdf",
            MAX_OUTPUT_FILENAME_BYTES,
            "PDF to PDF/A"
        );
    }

    @Override
    public List<OperationOutput> execute(OperationContext context) {
        PdfAPlanFactory.PdfAPlan plan = planFactory.create(
            context.options()
        );
        PdfAEngine.PdfAResult result = engine.convert(
            context.inputs().getFirst(),
            context.options(),
            context.workspace(),
            context::reportProgress,
            context::checkCancelled
        );
        String pdfFilename = outputFilename(
            context.options(),
            context.inputs().getFirst().originalFilename(),
            plan.profile().option()
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
            String sourceFilename,
            String profile) {
        String requested = options.path("outputFilename").asText("").trim();
        if (!requested.isEmpty()) {
            PdfOperationValidation.validateOutputFilename(
                requested,
                ".pdf",
                MAX_OUTPUT_FILENAME_BYTES,
                "PDF to PDF/A"
            );
            return FilenameSanitizer.sanitize(
                requested,
                "document-" + profile + ".pdf"
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
            base + "-" + profile + ".pdf",
            "document-" + profile + ".pdf"
        );
    }

    private String reportFilename(String pdfFilename) {
        String base = pdfFilename.toLowerCase(Locale.ROOT).endsWith(".pdf")
            ? pdfFilename.substring(0, pdfFilename.length() - 4)
            : pdfFilename;
        return FilenameSanitizer.sanitize(
            base + "-validation-report.json",
            "pdfa-validation-report.json"
        );
    }
}
