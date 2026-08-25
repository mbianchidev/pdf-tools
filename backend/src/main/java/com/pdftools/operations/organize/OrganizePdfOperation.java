package com.pdftools.operations.organize;

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
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class OrganizePdfOperation implements PdfOperation {

    private static final int MAX_OUTPUT_FILENAME_BYTES = 120;

    private final PdfSplitEngine pageCopyEngine;
    private final OrganizePlanFactory planFactory;

    public OrganizePdfOperation(
            PdfSplitEngine pageCopyEngine,
            OrganizePlanFactory planFactory) {
        this.pageCopyEngine = pageCopyEngine;
        this.planFactory = planFactory;
    }

    @Override
    public String key() {
        return "organize";
    }

    @Override
    public void validateSubmission(OperationSubmission submission) {
        PdfOperationValidation.requireSinglePdf(submission, "Organize PDF");
        planFactory.validateShape(submission.options());
        PdfOperationValidation.validateOptionalOutputFilename(
            submission.options(),
            ".pdf",
            MAX_OUTPUT_FILENAME_BYTES,
            "Organize PDF"
        );
    }

    @Override
    public List<OperationOutput> execute(OperationContext context) {
        AtomicReference<OrganizePlanFactory.OrganizePlan> plan =
            new AtomicReference<>();
        Path output = pageCopyEngine.copySelectedPages(
            context.inputs().getFirst().path(),
            context.workspace(),
            pageCount -> {
                OrganizePlanFactory.OrganizePlan resolved =
                    planFactory.create(context.options(), pageCount);
                plan.set(resolved);
                return resolved.pages().stream()
                    .map(OrganizePlanFactory.OrganizedPage::sourcePage)
                    .toList();
            },
            (document, page, sourcePageNumber, outputPosition) -> {
                int rotation = plan.get()
                    .pages()
                    .get(outputPosition - 1)
                    .rotation();
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
                "Organize PDF"
            );
            return FilenameSanitizer.sanitize(requested, "organized.pdf");
        }
        return FilenameSanitizer.withSuffix(sourceFilename, "_organized");
    }
}
