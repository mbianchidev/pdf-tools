package com.pdftools.operations.rotate;

import com.pdftools.operations.OperationContext;
import com.pdftools.operations.OperationOutput;
import com.pdftools.operations.OperationSubmission;
import com.pdftools.operations.PdfOperation;
import com.pdftools.operations.PdfOperationValidation;
import com.pdftools.operations.split.PdfSplitEngine;
import com.pdftools.util.FilenameSanitizer;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class RotatePdfOperation implements PdfOperation {

    private static final int MAX_OUTPUT_FILENAME_BYTES = 120;

    private final PdfSplitEngine pageCopyEngine;
    private final RotatePlanFactory planFactory;

    public RotatePdfOperation(
            PdfSplitEngine pageCopyEngine,
            RotatePlanFactory planFactory) {
        this.pageCopyEngine = pageCopyEngine;
        this.planFactory = planFactory;
    }

    @Override
    public String key() {
        return "rotate";
    }

    @Override
    public void validateSubmission(OperationSubmission submission) {
        PdfOperationValidation.requireSinglePdf(submission, "Rotate PDF");
        planFactory.validateShape(submission.options());
        PdfOperationValidation.validateOptionalOutputFilename(
            submission.options(),
            ".pdf",
            MAX_OUTPUT_FILENAME_BYTES,
            "Rotate PDF"
        );
    }

    @Override
    public List<OperationOutput> execute(OperationContext context) {
        AtomicReference<RotatePlanFactory.RotatePlan> plan =
            new AtomicReference<>();
        Path output = pageCopyEngine.copySelectedPages(
            context.inputs().getFirst().path(),
            context.workspace(),
            pageCount -> {
                plan.set(planFactory.create(context.options(), pageCount));
                List<Integer> allPages = new ArrayList<>(pageCount);
                for (int page = 1; page <= pageCount; page++) {
                    allPages.add(page);
                }
                return List.copyOf(allPages);
            },
            (document, page, sourcePageNumber, outputPosition) -> {
                int rotation = plan.get().rotationFor(sourcePageNumber);
                if (rotation != 0) {
                    page.setRotation(Math.floorMod(
                        page.getRotation() + rotation,
                        360
                    ));
                }
            },
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
                "Rotate PDF"
            );
            return FilenameSanitizer.sanitize(requested, "rotated.pdf");
        }
        return FilenameSanitizer.withSuffix(sourceFilename, "_rotated");
    }
}
