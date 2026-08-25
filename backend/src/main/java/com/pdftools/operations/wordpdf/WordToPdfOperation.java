package com.pdftools.operations.wordpdf;

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
public class WordToPdfOperation implements PdfOperation {

    private static final int MAX_OUTPUT_FILENAME_BYTES = 120;

    private final WordToPdfConverter converter;
    private final WordDocumentValidator validator;

    public WordToPdfOperation(
            WordToPdfConverter converter,
            WordDocumentValidator validator) {
        this.converter = converter;
        this.validator = validator;
    }

    @Override
    public String key() {
        return "word-to-pdf";
    }

    @Override
    public void validateSubmission(OperationSubmission submission) {
        validator.validateSubmission(submission);
        PdfOperationValidation.validateOptionalOutputFilename(
            submission.options(),
            ".pdf",
            MAX_OUTPUT_FILENAME_BYTES,
            "Word to PDF"
        );
    }

    @Override
    public List<OperationOutput> execute(OperationContext context) {
        return List.of(new OperationOutput(
            converter.convert(
                context.inputs().getFirst(),
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
                "Word to PDF"
            );
            return FilenameSanitizer.sanitize(requested, "document.pdf");
        }
        String sanitized = FilenameSanitizer.sanitize(
            sourceFilename,
            "document.docx"
        );
        String lower = sanitized.toLowerCase(Locale.ROOT);
        int extensionLength = lower.endsWith(".docx") ? 5 : 4;
        return FilenameSanitizer.sanitize(
            sanitized.substring(0, sanitized.length() - extensionLength)
                + ".pdf",
            "document.pdf"
        );
    }
}
