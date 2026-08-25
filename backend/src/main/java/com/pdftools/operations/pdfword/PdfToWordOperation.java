package com.pdftools.operations.pdfword;

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
public class PdfToWordOperation implements PdfOperation {

    private static final int MAX_OUTPUT_FILENAME_BYTES = 120;
    private static final Set<String> MEDIA_TYPES = Set.of(
        "application/pdf",
        "application/octet-stream"
    );

    private final PdfToWordEngine engine;
    private final PdfToWordPlanFactory planFactory;
    private final PdfToWordProperties properties;

    public PdfToWordOperation(
            PdfToWordEngine engine,
            PdfToWordPlanFactory planFactory,
            PdfToWordProperties properties) {
        this.engine = engine;
        this.planFactory = planFactory;
        this.properties = properties;
    }

    @Override
    public String key() {
        return "pdf-to-word";
    }

    @Override
    public void validateSubmission(OperationSubmission submission) {
        if (submission.files().size() != 1) {
            throw new OperationException(
                "INVALID_FILE_COUNT",
                "PDF to Word requires exactly one PDF"
            );
        }
        OperationSubmission.UploadDescriptor file =
            submission.files().getFirst();
        if (!file.filename().toLowerCase(Locale.ROOT).endsWith(".pdf")
                || !MEDIA_TYPES.contains(
                    file.mediaType().toLowerCase(Locale.ROOT))) {
            throw new OperationException(
                "INVALID_PDF_FILE",
                "PDF to Word accepts one PDF file"
            );
        }
        if (file.sizeBytes() < 1
                || file.sizeBytes() > properties.getMaxInputBytes()) {
            throw new OperationException(
                "PDF_WORD_INPUT_LIMIT_EXCEEDED",
                "The PDF exceeds the configured input limit"
            );
        }
        planFactory.create(submission.options());
        PdfOperationValidation.validateOptionalOutputFilename(
            submission.options(),
            ".docx",
            MAX_OUTPUT_FILENAME_BYTES,
            "PDF to Word"
        );
    }

    @Override
    public List<OperationOutput> execute(OperationContext context) {
        PdfToWordPlanFactory.PdfToWordPlan plan = planFactory.create(
            context.options()
        );
        return List.of(new OperationOutput(
            engine.convert(
                context.inputs().getFirst().path(),
                plan,
                context.workspace(),
                context::reportProgress,
                context::checkCancelled
            ),
            outputFilename(
                context.options(),
                context.inputs().getFirst().originalFilename()
            ),
            "application/vnd.openxmlformats-officedocument."
                + "wordprocessingml.document"
        ));
    }

    private String outputFilename(
            JsonNode options,
            String sourceFilename) {
        String requested = options.path("outputFilename").asText("").trim();
        if (!requested.isEmpty()) {
            PdfOperationValidation.validateOutputFilename(
                requested,
                ".docx",
                MAX_OUTPUT_FILENAME_BYTES,
                "PDF to Word"
            );
            return FilenameSanitizer.sanitize(
                requested,
                "document.docx"
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
            base + ".docx",
            "document.docx"
        );
    }
}
