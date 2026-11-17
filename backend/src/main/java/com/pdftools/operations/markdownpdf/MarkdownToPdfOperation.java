package com.pdftools.operations.markdownpdf;

import com.pdftools.operations.OperationContext;
import com.pdftools.operations.OperationOutput;
import com.pdftools.operations.OperationSubmission;
import com.pdftools.operations.PdfOperation;
import com.pdftools.operations.PdfOperationValidation;
import com.pdftools.operations.htmlpdf.HtmlPlanFactory;
import com.pdftools.util.FilenameSanitizer;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.List;

@Component
public class MarkdownToPdfOperation implements PdfOperation {

    private static final int MAX_OUTPUT_FILENAME_BYTES = 120;

    private final MarkdownToPdfConverter converter;
    private final MarkdownDocumentValidator validator;
    private final HtmlPlanFactory planFactory;

    public MarkdownToPdfOperation(
            MarkdownToPdfConverter converter,
            MarkdownDocumentValidator validator,
            HtmlPlanFactory planFactory) {
        this.converter = converter;
        this.validator = validator;
        this.planFactory = planFactory;
    }

    @Override
    public String key() {
        return "markdown-to-pdf";
    }

    @Override
    public void validateSubmission(OperationSubmission submission) {
        validator.validateSubmission(submission);
        planFactory.create(submission.options());
        PdfOperationValidation.validateOptionalOutputFilename(
            submission.options(),
            ".pdf",
            MAX_OUTPUT_FILENAME_BYTES,
            "Markdown to PDF"
        );
    }

    @Override
    public List<OperationOutput> execute(OperationContext context) {
        return List.of(new OperationOutput(
            converter.convert(
                context.inputs().getFirst(),
                context.options(),
                context.workspace(),
                context::reportProgress,
                context::checkCancelled
            ),
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
                "Markdown to PDF"
            );
            return FilenameSanitizer.sanitize(requested, "document.pdf");
        }
        String sanitized = FilenameSanitizer.sanitize(
            sourceFilename,
            "document.md"
        );
        return FilenameSanitizer.sanitize(
            MarkdownSourceKind.stripExtension(sanitized) + ".pdf",
            "document.pdf"
        );
    }
}
