package com.pdftools.operations.redact;

import com.pdftools.operations.OperationContext;
import com.pdftools.operations.OperationOutput;
import com.pdftools.operations.OperationSubmission;
import com.pdftools.operations.PdfOperation;
import com.pdftools.operations.PdfOperationValidation;
import com.pdftools.util.FilenameSanitizer;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.nio.file.Path;
import java.util.List;

@Component
public class RedactPdfOperation implements PdfOperation {

    private static final int MAX_OUTPUT_FILENAME_BYTES = 120;

    private final RedactEngine engine;
    private final RedactPlanFactory planFactory;

    public RedactPdfOperation(
            RedactEngine engine,
            RedactPlanFactory planFactory) {
        this.engine = engine;
        this.planFactory = planFactory;
    }

    @Override
    public String key() {
        return "redact";
    }

    @Override
    public void validateSubmission(OperationSubmission submission) {
        PdfOperationValidation.requireSinglePdf(
            submission,
            "Redact PDF"
        );
        planFactory.validateShape(submission.options());
        PdfOperationValidation.validateOptionalOutputFilename(
            submission.options(),
            ".pdf",
            MAX_OUTPUT_FILENAME_BYTES,
            "Redact PDF"
        );
    }

    @Override
    public List<OperationOutput> execute(OperationContext context) {
        Path output = engine.redact(
            context.inputs().getFirst().path(),
            context.inputs().getFirst().sha256(),
            context.options(),
            context.workspace(),
            context::reportProgress,
            context::checkCancelled
        );
        return List.of(new OperationOutput(
            output,
            outputFilename(
                context.options(),
                context.inputs().getFirst().originalFilename()
            ),
            "application/pdf"
        ));
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
                "Redact PDF"
            );
            return FilenameSanitizer.sanitize(requested, "redacted.pdf");
        }
        return FilenameSanitizer.withSuffix(
            sourceFilename,
            "_redacted"
        );
    }
}
