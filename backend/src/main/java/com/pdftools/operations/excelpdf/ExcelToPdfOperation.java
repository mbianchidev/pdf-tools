package com.pdftools.operations.excelpdf;

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
public class ExcelToPdfOperation implements PdfOperation {

    private static final int MAX_OUTPUT_FILENAME_BYTES = 120;

    private final ExcelToPdfConverter converter;
    private final ExcelDocumentValidator validator;
    private final ExcelPlanFactory planFactory;

    public ExcelToPdfOperation(
            ExcelToPdfConverter converter,
            ExcelDocumentValidator validator,
            ExcelPlanFactory planFactory) {
        this.converter = converter;
        this.validator = validator;
        this.planFactory = planFactory;
    }

    @Override
    public String key() {
        return "excel-to-pdf";
    }

    @Override
    public void validateSubmission(OperationSubmission submission) {
        validator.validateSubmission(submission);
        planFactory.create(
            submission.options(),
            planFactory.spreadsheetVersion(
                submission.files().getFirst().filename()
            )
        );
        PdfOperationValidation.validateOptionalOutputFilename(
            submission.options(),
            ".pdf",
            MAX_OUTPUT_FILENAME_BYTES,
            "Excel to PDF"
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
                "Excel to PDF"
            );
            return FilenameSanitizer.sanitize(
                requested,
                "workbook.pdf"
            );
        }
        String sanitized = FilenameSanitizer.sanitize(
            sourceFilename,
            "workbook.xlsx"
        );
        String lower = sanitized.toLowerCase(Locale.ROOT);
        int extensionLength = lower.endsWith(".xlsx") ? 5 : 4;
        return FilenameSanitizer.sanitize(
            sanitized.substring(0, sanitized.length() - extensionLength)
                + ".pdf",
            "workbook.pdf"
        );
    }
}
