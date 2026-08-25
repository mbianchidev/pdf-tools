package com.pdftools.operations.compare;

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
public class ComparePdfOperation implements PdfOperation {

    private static final int MAX_OUTPUT_FILENAME_BYTES = 160;
    private static final Set<String> MEDIA_TYPES = Set.of(
        "application/pdf",
        "application/octet-stream"
    );

    private final ComparePdfEngine engine;
    private final ComparePlanFactory planFactory;
    private final CompareProperties properties;

    public ComparePdfOperation(
            ComparePdfEngine engine,
            ComparePlanFactory planFactory,
            CompareProperties properties) {
        this.engine = engine;
        this.planFactory = planFactory;
        this.properties = properties;
    }

    @Override
    public String key() {
        return "compare";
    }

    @Override
    public void validateSubmission(OperationSubmission submission) {
        if (submission.files().size() != 2) {
            throw new OperationException(
                "INVALID_FILE_COUNT",
                "Compare PDF requires a baseline and candidate PDF"
            );
        }
        long total = 0;
        for (OperationSubmission.UploadDescriptor file :
                submission.files()) {
            if (!file.filename().toLowerCase(Locale.ROOT)
                    .endsWith(".pdf")
                    || !MEDIA_TYPES.contains(
                        file.mediaType().toLowerCase(Locale.ROOT))) {
                throw new OperationException(
                    "INVALID_PDF_FILE",
                    "Compare PDF accepts exactly two PDF files"
                );
            }
            if (file.sizeBytes() < 1
                    || file.sizeBytes() > properties.getMaxInputBytes()) {
                throw new OperationException(
                    "COMPARE_INPUT_LIMIT_EXCEEDED",
                    "A PDF exceeds the comparison input limit"
                );
            }
            try {
                total = Math.addExact(total, file.sizeBytes());
            } catch (ArithmeticException exception) {
                throw totalInputLimit();
            }
        }
        if (total > properties.getMaxTotalInputBytes()) {
            throw totalInputLimit();
        }
        planFactory.create(submission.options());
        PdfOperationValidation.validateOptionalOutputFilename(
            submission.options(),
            ".zip",
            MAX_OUTPUT_FILENAME_BYTES,
            "Compare PDF"
        );
    }

    @Override
    public List<OperationOutput> execute(OperationContext context) {
        ComparePdfEngine.CompareResult result = engine.compare(
            context.inputs().get(0),
            context.inputs().get(1),
            context.options(),
            context.workspace(),
            context::reportProgress,
            context::checkCancelled
        );
        String archiveFilename = outputFilename(
            context.options(),
            context.inputs().get(0).originalFilename(),
            context.inputs().get(1).originalFilename()
        );
        return List.of(
            new OperationOutput(
                result.archive(),
                archiveFilename,
                "application/zip"
            ),
            new OperationOutput(
                result.report(),
                reportFilename(archiveFilename),
                "application/json"
            )
        );
    }

    private String outputFilename(
            JsonNode options,
            String baselineFilename,
            String candidateFilename) {
        String requested = options.path("outputFilename").asText("").trim();
        if (!requested.isEmpty()) {
            PdfOperationValidation.validateOutputFilename(
                requested,
                ".zip",
                MAX_OUTPUT_FILENAME_BYTES,
                "Compare PDF"
            );
            return FilenameSanitizer.sanitize(
                requested,
                "comparison.zip"
            );
        }
        return FilenameSanitizer.sanitize(
            base(baselineFilename)
                + "-vs-" + base(candidateFilename)
                + "-comparison.zip",
            "comparison.zip"
        );
    }

    private String reportFilename(String archiveFilename) {
        String base = archiveFilename.toLowerCase(Locale.ROOT)
            .endsWith(".zip")
                ? archiveFilename.substring(
                    0,
                    archiveFilename.length() - 4
                )
                : archiveFilename;
        return FilenameSanitizer.sanitize(
            base + "-report.json",
            "comparison-report.json"
        );
    }

    private String base(String filename) {
        String sanitized = FilenameSanitizer.sanitize(
            filename,
            "document.pdf"
        );
        return sanitized.toLowerCase(Locale.ROOT).endsWith(".pdf")
            ? sanitized.substring(0, sanitized.length() - 4)
            : sanitized;
    }

    private OperationException totalInputLimit() {
        return new OperationException(
            "COMPARE_TOTAL_INPUT_LIMIT_EXCEEDED",
            "The PDFs exceed the total comparison input limit"
        );
    }
}
