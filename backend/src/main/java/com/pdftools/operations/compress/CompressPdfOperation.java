package com.pdftools.operations.compress;

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
public class CompressPdfOperation implements PdfOperation {

    private static final int MAX_OUTPUT_FILENAME_BYTES = 120;
    private static final Set<String> MEDIA_TYPES = Set.of(
        "application/pdf",
        "application/octet-stream"
    );

    private final CompressPdfEngine engine;
    private final CompressPdfPlanFactory planFactory;
    private final CompressPdfProperties properties;

    public CompressPdfOperation(
            CompressPdfEngine engine,
            CompressPdfPlanFactory planFactory,
            CompressPdfProperties properties) {
        this.engine = engine;
        this.planFactory = planFactory;
        this.properties = properties;
    }

    @Override
    public String key() {
        return "compress";
    }

    @Override
    public void validateSubmission(OperationSubmission submission) {
        if (submission.files().size() != 1) {
            throw new OperationException(
                "INVALID_FILE_COUNT",
                "Compress PDF requires exactly one PDF"
            );
        }
        OperationSubmission.UploadDescriptor file =
            submission.files().getFirst();
        if (!file.filename().toLowerCase(Locale.ROOT).endsWith(".pdf")
                || !MEDIA_TYPES.contains(
                    file.mediaType().toLowerCase(Locale.ROOT))) {
            throw new OperationException(
                "INVALID_PDF_FILE",
                "Compress PDF accepts one PDF file"
            );
        }
        if (file.sizeBytes() < 1
                || file.sizeBytes() > properties.getMaxInputBytes()) {
            throw new OperationException(
                "COMPRESS_INPUT_LIMIT_EXCEEDED",
                "The PDF exceeds the configured compression input limit"
            );
        }
        planFactory.create(submission.options());
        PdfOperationValidation.validateOptionalOutputFilename(
            submission.options(),
            ".pdf",
            MAX_OUTPUT_FILENAME_BYTES,
            "Compress PDF"
        );
    }

    @Override
    public List<OperationOutput> execute(OperationContext context) {
        var input = context.inputs().getFirst();
        var plan = planFactory.create(context.options());
        return List.of(new OperationOutput(
            engine.compress(
                input.path(),
                input.sha256(),
                plan,
                context.workspace(),
                context::reportProgress,
                context::checkCancelled
            ),
            outputFilename(
                context.options(),
                input.originalFilename()
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
                "Compress PDF"
            );
            return FilenameSanitizer.sanitize(
                requested,
                "compressed.pdf"
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
            base + "-compressed.pdf",
            "compressed.pdf"
        );
    }
}
