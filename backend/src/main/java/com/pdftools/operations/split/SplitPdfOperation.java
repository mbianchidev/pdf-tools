package com.pdftools.operations.split;

import com.pdftools.operations.OperationContext;
import com.pdftools.operations.OperationException;
import com.pdftools.operations.OperationOutput;
import com.pdftools.operations.OperationSubmission;
import com.pdftools.operations.PdfOperation;
import com.pdftools.operations.ZipArtifactService;
import com.pdftools.util.FilenameSanitizer;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class SplitPdfOperation implements PdfOperation {

    private static final Set<String> ACCEPTED_MEDIA_TYPES = Set.of(
        "application/pdf",
        "application/x-pdf",
        "application/octet-stream"
    );

    private final PdfSplitEngine splitEngine;
    private final ZipArtifactService zipArtifactService;
    private final SplitProperties properties;

    public SplitPdfOperation(
            PdfSplitEngine splitEngine,
            ZipArtifactService zipArtifactService,
            SplitProperties properties) {
        this.splitEngine = splitEngine;
        this.zipArtifactService = zipArtifactService;
        this.properties = properties;
    }

    @Override
    public String key() {
        return "split";
    }

    @Override
    public void validateSubmission(OperationSubmission submission) {
        if (submission.files().size() != 1) {
            throw new OperationException(
                "INVALID_FILE_COUNT",
                "Split requires exactly one PDF file"
            );
        }
        OperationSubmission.UploadDescriptor file = submission.files().getFirst();
        if (!hasPdfStem(file.filename())
                || !ACCEPTED_MEDIA_TYPES.contains(file.mediaType().toLowerCase(Locale.ROOT))) {
            throw new OperationException(
                "INVALID_FILE_TYPE",
                "Split input must be a PDF"
            );
        }

        JsonNode options = submission.options();
        SplitMode mode = SplitMode.parse(options.path("mode").asText("individual"));
        if (mode == SplitMode.FIXED) {
            JsonNode size = options.get("fixedGroupSize");
            if (size == null || !size.canConvertToInt()
                    || size.asInt() < 1
                    || size.asInt() > properties.getMaxFixedGroupSize()) {
                throw new OperationException(
                    "INVALID_FIXED_GROUP_SIZE",
                    "fixedGroupSize must be between 1 and "
                        + properties.getMaxFixedGroupSize()
                );
            }
        }
        if (mode == SplitMode.RANGES) {
            JsonNode ranges = options.get("ranges");
            if (ranges == null || !ranges.isArray() || ranges.isEmpty()) {
                throw new OperationException(
                    "SPLIT_RANGES_REQUIRED",
                    "ranges mode requires at least one page expression"
                );
            }
            if (ranges.size() > properties.getMaxOutputs()) {
                throw new OperationException(
                    "SPLIT_OUTPUT_LIMIT_EXCEEDED",
                    "Too many split ranges"
                );
            }
            for (JsonNode range : ranges) {
                if (!range.isTextual() || range.asText().isBlank()) {
                    throw new OperationException(
                        "INVALID_SPLIT_RANGE",
                        "Every split range must be a non-empty string"
                    );
                }
            }
        }
        if (options.has("outputFilename")) {
            if (!options.get("outputFilename").isTextual()) {
                throw new OperationException(
                    "INVALID_OPTIONS",
                    "outputFilename must be a string"
                );
            }
            String outputFilename = options.get("outputFilename").asText().trim();
            if (!outputFilename.isEmpty()) {
                validateOutputFilename(outputFilename);
            }
        }
    }

    @Override
    public List<OperationOutput> execute(OperationContext context) {
        SplitResult result = splitEngine.split(
            context.inputs().getFirst().path(),
            context.options(),
            context.workspace(),
            context::reportProgress,
            context::checkCancelled
        );
        String firstFilename = context.inputs().getFirst().originalFilename();
        SplitMode mode = SplitMode.parse(context.options().path("mode").asText("individual"));
        List<OperationOutput> parts = new ArrayList<>(result.parts().size());
        for (SplitResult.Part part : result.parts()) {
            String suffix = mode == SplitMode.INDIVIDUAL
                ? String.format(Locale.ROOT, "_page_%04d", part.pages().getFirst())
                : String.format(Locale.ROOT, "_part_%04d", part.position());
            parts.add(new OperationOutput(
                part.path(),
                FilenameSanitizer.withSuffix(firstFilename, suffix),
                "application/pdf"
            ));
        }
        context.reportProgress(93);
        Path zipPath = context.workspace().resolve("split.zip");
        OperationOutput zip = zipArtifactService.create(
            parts,
            zipPath,
            outputFilename(context.options(), firstFilename),
            Math.addExact(
                properties.getMaxTotalOutputBytes(),
                1024L * 1024L
            ),
            context::checkCancelled,
            true
        );
        context.reportProgress(97);
        return List.of(zip);
    }

    private String outputFilename(JsonNode options, String firstFilename) {
        String requested = options.path("outputFilename").asText("").trim();
        if (!requested.isEmpty()) {
            validateOutputFilename(requested);
            return FilenameSanitizer.sanitize(requested, "split.zip");
        }
        String pdfName = FilenameSanitizer.withSuffix(firstFilename, "_split");
        return pdfName.substring(0, pdfName.length() - 4) + ".zip";
    }

    private void validateOutputFilename(String outputFilename) {
        if (!outputFilename.toLowerCase(Locale.ROOT).endsWith(".zip")
                || outputFilename.getBytes(StandardCharsets.UTF_8).length > 120) {
            throw new OperationException(
                "INVALID_OUTPUT_FILENAME",
                "Split outputFilename must end with .zip and stay within 120 bytes",
                Map.of("maxBytes", 120)
            );
        }
    }

    private boolean hasPdfStem(String filename) {
        String lower = filename.toLowerCase(Locale.ROOT);
        return lower.endsWith(".pdf")
            && !filename.substring(0, filename.length() - 4).isBlank();
    }
}
