package com.pdftools.operations.jpgpdf;

import com.pdftools.operations.OperationContext;
import com.pdftools.operations.OperationException;
import com.pdftools.operations.OperationOutput;
import com.pdftools.operations.OperationSubmission;
import com.pdftools.operations.PdfOperation;
import com.pdftools.operations.PdfOperationValidation;
import com.pdftools.util.FilenameSanitizer;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class JpgToPdfOperation implements PdfOperation {

    private static final Set<String> JPEG_MEDIA_TYPES = Set.of(
        "image/jpeg",
        "image/jpg",
        "application/octet-stream"
    );
    private static final int MAX_OUTPUT_FILENAME_BYTES = 120;

    private final JpgToPdfEngine engine;
    private final JpgToPdfPlanFactory planFactory;
    private final JpgToPdfProperties properties;

    public JpgToPdfOperation(
            JpgToPdfEngine engine,
            JpgToPdfPlanFactory planFactory,
            JpgToPdfProperties properties) {
        this.engine = engine;
        this.planFactory = planFactory;
        this.properties = properties;
    }

    @Override
    public String key() {
        return "jpg-to-pdf";
    }

    @Override
    public void validateSubmission(OperationSubmission submission) {
        if (submission.files().isEmpty()
                || submission.files().size() > properties.getMaxImages()) {
            throw new OperationException(
                "INVALID_FILE_COUNT",
                "JPG to PDF requires between 1 and "
                    + properties.getMaxImages()
                    + " JPEG images"
            );
        }
        long totalBytes = 0;
        for (OperationSubmission.UploadDescriptor file :
                submission.files()) {
            String lowerName = file.filename().toLowerCase(Locale.ROOT);
            String lowerType = file.mediaType().toLowerCase(Locale.ROOT);
            if (!hasJpegStem(file.filename(), lowerName)
                    || !JPEG_MEDIA_TYPES.contains(lowerType)) {
                throw new OperationException(
                    "INVALID_FILE_TYPE",
                    "JPG to PDF inputs must be JPEG images"
                );
            }

            try {
                totalBytes = Math.addExact(totalBytes, file.sizeBytes());
            } catch (ArithmeticException exception) {
                throw totalInputLimit();
            }
        }
        if (totalBytes > properties.getMaxTotalInputBytes()) {
            throw totalInputLimit();
        }
        planFactory.validateShape(submission.options());
        PdfOperationValidation.validateOptionalOutputFilename(
            submission.options(),
            ".pdf",
            MAX_OUTPUT_FILENAME_BYTES,
            "JPG to PDF"
        );
    }

    private boolean hasJpegStem(String filename, String lowerName) {
        int extensionLength = lowerName.endsWith(".jpeg")
            ? 5
            : lowerName.endsWith(".jpg") ? 4 : 0;
        return extensionLength > 0
            && !filename.substring(
                0,
                filename.length() - extensionLength
            ).isBlank();
    }

    @Override
    public List<OperationOutput> execute(OperationContext context) {
        Path output = engine.create(
            context.inputs(),
            context.workspace(),
            planFactory.create(context.options()),
            context::reportProgress,
            context::checkCancelled
        );
        context.reportProgress(97);
        return List.of(new OperationOutput(
            output,
            outputFilename(context.options()),
            "application/pdf"
        ));
    }

    private String outputFilename(JsonNode options) {
        String requested = options.path("outputFilename").asText("").trim();
        if (!requested.isEmpty()) {
            PdfOperationValidation.validateOutputFilename(
                requested,
                ".pdf",
                MAX_OUTPUT_FILENAME_BYTES,
                "JPG to PDF"
            );
            return FilenameSanitizer.sanitize(
                requested,
                "images.pdf"
            );
        }
        return "images.pdf";
    }

    private OperationException totalInputLimit() {
        return new OperationException(
            "JPG_TOTAL_INPUT_SIZE_LIMIT_EXCEEDED",
            "JPEG inputs exceed the configured total size limit"
        );
    }
}
