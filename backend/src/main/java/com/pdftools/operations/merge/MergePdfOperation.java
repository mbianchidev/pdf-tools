package com.pdftools.operations.merge;

import com.pdftools.operations.OperationContext;
import com.pdftools.operations.OperationException;
import com.pdftools.operations.OperationInput;
import com.pdftools.operations.OperationOutput;
import com.pdftools.operations.OperationSubmission;
import com.pdftools.operations.PdfOperation;
import com.pdftools.util.FilenameSanitizer;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class MergePdfOperation implements PdfOperation {

    private static final Set<String> ACCEPTED_MEDIA_TYPES = Set.of(
        "application/pdf",
        "application/x-pdf",
        "application/octet-stream"
    );

    private final PdfMergeEngine mergeEngine;
    private final MergeProperties properties;

    public MergePdfOperation(PdfMergeEngine mergeEngine, MergeProperties properties) {
        this.mergeEngine = mergeEngine;
        this.properties = properties;
    }

    @Override
    public String key() {
        return "merge";
    }

    @Override
    public void validateSubmission(OperationSubmission submission) {
        List<OperationSubmission.UploadDescriptor> files = submission.files();
        if (files.size() < 2 || files.size() > properties.getMaxFiles()) {
            throw new OperationException(
                "INVALID_FILE_COUNT",
                "Merge requires between 2 and " + properties.getMaxFiles() + " PDF files",
                Map.of("maxFiles", properties.getMaxFiles())
            );
        }

        long totalBytes = 0;
        for (OperationSubmission.UploadDescriptor file : files) {
            String mediaType = file.mediaType().toLowerCase(Locale.ROOT);
            if (!hasPdfStem(file.filename())
                    || !ACCEPTED_MEDIA_TYPES.contains(mediaType)) {
                throw new OperationException(
                    "INVALID_FILE_TYPE",
                    "Every merge input must be a PDF",
                    Map.of("position", file.position(), "filename", file.filename())
                );
            }
            try {
                totalBytes = Math.addExact(totalBytes, file.sizeBytes());
            } catch (ArithmeticException exception) {
                throw inputTooLarge();
            }
        }
        if (totalBytes > properties.getMaxTotalInputBytes()) {
            throw inputTooLarge();
        }

        if (submission.options().has("outputFilename")
                && !submission.options().get("outputFilename").isTextual()) {
            throw new OperationException(
                "INVALID_OPTIONS",
                "outputFilename must be a string"
            );
        }
        if (submission.options().has("outputFilename")) {
            String outputFilename = submission.options().get("outputFilename").asText().trim();
            if (!outputFilename.isEmpty()) {
                validateOutputFilename(outputFilename);
            }
        }
    }

    @Override
    public List<OperationOutput> execute(OperationContext context) {
        context.reportProgress(5);
        List<MergeSource> sources = context.inputs().stream()
            .map(this::toSource)
            .toList();
        String outputFilename = outputFilename(context, sources.getFirst().filename());
        Path output = context.workspace().resolve("merged.pdf");

        mergeEngine.merge(
            sources,
            output,
            context::reportProgress,
            context::checkCancelled
        );
        return List.of(new OperationOutput(output, outputFilename, "application/pdf"));
    }

    private MergeSource toSource(OperationInput input) {
        return new MergeSource(
            input.position(),
            input.path(),
            input.originalFilename(),
            input.sizeBytes()
        );
    }

    private String outputFilename(OperationContext context, String firstFilename) {
        String requested = context.options().path("outputFilename").asText("").trim();
        if (requested.isEmpty()) {
            if (!hasPdfStem(firstFilename)) {
                return "merged.pdf";
            }
            return FilenameSanitizer.withSuffix(firstFilename, "_merged");
        }
        validateOutputFilename(requested);
        return FilenameSanitizer.sanitize(requested, "merged.pdf");
    }

    private void validateOutputFilename(String candidate) {
        if (!candidate.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            throw new OperationException(
                "INVALID_OUTPUT_FILENAME",
                "Merge outputFilename must end with .pdf"
            );
        }
        if (candidate.getBytes(StandardCharsets.UTF_8).length > 120) {
            throw new OperationException(
                "INVALID_OUTPUT_FILENAME",
                "Merge outputFilename exceeds the 120-byte limit"
            );
        }
    }

    private OperationException inputTooLarge() {
        return new OperationException(
            "MERGE_INPUT_TOO_LARGE",
            "Merge inputs exceed the configured total size limit",
            Map.of("maxTotalInputBytes", properties.getMaxTotalInputBytes())
        );
    }

    private boolean hasPdfStem(String filename) {
        String lower = filename.toLowerCase(Locale.ROOT);
        return lower.endsWith(".pdf")
            && !filename.substring(0, filename.length() - 4).isBlank();
    }
}
