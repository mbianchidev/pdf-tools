package com.pdftools.operations.crop;

import com.pdftools.operations.OperationContext;
import com.pdftools.operations.OperationOutput;
import com.pdftools.operations.OperationSubmission;
import com.pdftools.operations.PdfOperation;
import com.pdftools.operations.PdfOperationValidation;
import com.pdftools.operations.shared.coordinates.CoordinateTransformer;
import com.pdftools.operations.shared.coordinates.NormalizedRectangle;
import com.pdftools.operations.shared.coordinates.PageGeometry;
import com.pdftools.operations.shared.coordinates.PdfRectangle;
import com.pdftools.operations.split.PdfSplitEngine;
import com.pdftools.util.FilenameSanitizer;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class CropPdfOperation implements PdfOperation {

    private static final int MAX_OUTPUT_FILENAME_BYTES = 120;

    private final PdfSplitEngine pageCopyEngine;
    private final CropPlanFactory planFactory;
    private final CoordinateTransformer coordinateTransformer;

    public CropPdfOperation(
            PdfSplitEngine pageCopyEngine,
            CropPlanFactory planFactory,
            CoordinateTransformer coordinateTransformer) {
        this.pageCopyEngine = pageCopyEngine;
        this.planFactory = planFactory;
        this.coordinateTransformer = coordinateTransformer;
    }

    @Override
    public String key() {
        return "crop";
    }

    @Override
    public void validateSubmission(OperationSubmission submission) {
        PdfOperationValidation.requireSinglePdf(submission, "Crop PDF");
        planFactory.validateShape(submission.options());
        PdfOperationValidation.validateOptionalOutputFilename(
            submission.options(),
            ".pdf",
            MAX_OUTPUT_FILENAME_BYTES,
            "Crop PDF"
        );
    }

    @Override
    public List<OperationOutput> execute(OperationContext context) {
        AtomicReference<CropPlanFactory.CropPlan> plan =
            new AtomicReference<>();
        Path output = pageCopyEngine.copySelectedPages(
            context.inputs().getFirst().path(),
            context.workspace(),
            pageCount -> {
                plan.set(planFactory.create(context.options(), pageCount));
                List<Integer> pages = new ArrayList<>(pageCount);
                for (int page = 1; page <= pageCount; page++) {
                    pages.add(page);
                }
                return List.copyOf(pages);
            },
            (document, page, sourcePageNumber, outputPosition) -> {
                NormalizedRectangle crop = plan.get().cropFor(
                    sourcePageNumber
                );
                if (crop == null) {
                    return;
                }
                PDRectangle sourceBox = page.getCropBox();
                PdfRectangle transformed =
                    coordinateTransformer.toPdfRectangle(
                        crop,
                        new PageGeometry(
                            sourceBox.getLowerLeftX(),
                            sourceBox.getLowerLeftY(),
                            sourceBox.getWidth(),
                            sourceBox.getHeight(),
                            page.getRotation()
                        )
                    );
                page.setCropBox(new PDRectangle(
                    (float) transformed.x(),
                    (float) transformed.y(),
                    (float) transformed.width(),
                    (float) transformed.height()
                ));
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
                "Crop PDF"
            );
            return FilenameSanitizer.sanitize(requested, "cropped.pdf");
        }
        return FilenameSanitizer.withSuffix(sourceFilename, "_cropped");
    }
}
