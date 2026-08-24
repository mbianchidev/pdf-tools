package com.pdftools.operations.unlock;

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
public class UnlockPdfOperation implements PdfOperation {

    private static final int MAX_OUTPUT_FILENAME_BYTES = 120;

    private final PdfUnlockEngine unlockEngine;
    private final UnlockPlanFactory planFactory;

    public UnlockPdfOperation(
            PdfUnlockEngine unlockEngine,
            UnlockPlanFactory planFactory) {
        this.unlockEngine = unlockEngine;
        this.planFactory = planFactory;
    }

    @Override
    public String key() {
        return "unlock";
    }

    @Override
    public boolean hasSensitiveOptions() {
        return true;
    }

    @Override
    public void validateSubmission(OperationSubmission submission) {
        PdfOperationValidation.requireSinglePdf(submission, "Unlock PDF");
        planFactory.validateShape(submission.options());
        PdfOperationValidation.validateOptionalOutputFilename(
            submission.options(),
            ".pdf",
            MAX_OUTPUT_FILENAME_BYTES,
            "Unlock PDF"
        );
    }

    @Override
    public List<OperationOutput> execute(OperationContext context) {
        Path output = unlockEngine.unlock(
            context.inputs().getFirst().path(),
            context.workspace(),
            planFactory.create(context.options()),
            context::reportProgress,
            context::checkCancelled
        );
        context.reportProgress(97);
        return List.of(new OperationOutput(
            output,
            outputFilename(
                context.options(),
                context.inputs().getFirst().originalFilename()
            ),
            "application/pdf"
        ));
    }

    private String outputFilename(JsonNode options, String sourceFilename) {
        String requested = options.path("outputFilename").asText("").trim();
        if (!requested.isEmpty()) {
            PdfOperationValidation.validateOutputFilename(
                requested,
                ".pdf",
                MAX_OUTPUT_FILENAME_BYTES,
                "Unlock PDF"
            );
            return FilenameSanitizer.sanitize(requested, "unlocked.pdf");
        }
        return FilenameSanitizer.withSuffix(sourceFilename, "_unlocked");
    }
}
