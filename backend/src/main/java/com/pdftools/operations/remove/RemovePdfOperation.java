package com.pdftools.operations.remove;

import com.pdftools.operations.OperationContext;
import com.pdftools.operations.OperationException;
import com.pdftools.operations.OperationOutput;
import com.pdftools.operations.OperationSubmission;
import com.pdftools.operations.PdfOperation;
import com.pdftools.operations.PdfOperationValidation;
import com.pdftools.operations.split.PdfSplitEngine;
import com.pdftools.util.FilenameSanitizer;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.nio.file.Path;
import java.util.List;

@Component
public class RemovePdfOperation implements PdfOperation {

    private static final int MAX_OUTPUT_FILENAME_BYTES = 120;

    private final PdfSplitEngine pageCopyEngine;
    private final RemovePagePlanner pagePlanner;

    public RemovePdfOperation(
            PdfSplitEngine pageCopyEngine,
            RemovePagePlanner pagePlanner) {
        this.pageCopyEngine = pageCopyEngine;
        this.pagePlanner = pagePlanner;
    }

    @Override
    public String key() {
        return "remove";
    }

    @Override
    public void validateSubmission(OperationSubmission submission) {
        PdfOperationValidation.requireSinglePdf(submission, "Remove Pages");
        JsonNode pages = submission.options().get("pages");
        if (pages == null || pages.isNull()
                || pages.isTextual() && pages.asText().isBlank()) {
            throw new OperationException(
                "REMOVE_PAGES_REQUIRED",
                "Enter at least one page or range to remove"
            );
        }
        if (!pages.isTextual()) {
            throw new OperationException(
                "INVALID_REMOVE_PAGES",
                "pages must be a page-expression string"
            );
        }
        PdfOperationValidation.validateOptionalOutputFilename(
            submission.options(),
            ".pdf",
            MAX_OUTPUT_FILENAME_BYTES,
            "Remove Pages"
        );
    }

    @Override
    public List<OperationOutput> execute(OperationContext context) {
        String expression = context.options().path("pages").asText("");
        Path output = pageCopyEngine.copySelectedPages(
            context.inputs().getFirst().path(),
            context.workspace(),
            pageCount -> pagePlanner.remainingPages(expression, pageCount),
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
                "Remove Pages"
            );
            return FilenameSanitizer.sanitize(
                requested,
                "pages_removed.pdf"
            );
        }
        return FilenameSanitizer.withSuffix(
            sourceFilename,
            "_pages_removed"
        );
    }
}
