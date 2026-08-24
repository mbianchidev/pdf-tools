package com.pdftools.operations.edit;

import com.pdftools.operations.OperationContext;
import com.pdftools.operations.OperationException;
import com.pdftools.operations.OperationOutput;
import com.pdftools.operations.OperationSubmission;
import com.pdftools.operations.PdfOperation;
import com.pdftools.operations.PdfOperationValidation;
import com.pdftools.operations.split.PdfSplitEngine;
import com.pdftools.operations.watermark.WatermarkImagePreparer;
import com.pdftools.util.FilenameSanitizer;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class EditPdfOperation implements PdfOperation {

    private static final int MAX_OUTPUT_FILENAME_BYTES = 120;
    private static final Set<String> IMAGE_MEDIA_TYPES = Set.of(
        "image/png",
        "image/jpeg",
        "image/jpg",
        "application/octet-stream"
    );

    private final PdfSplitEngine pageCopyEngine;
    private final EditPlanFactory planFactory;
    private final EditProperties properties;
    private final WatermarkImagePreparer imagePreparer;
    private final EditRenderer renderer;

    public EditPdfOperation(
            PdfSplitEngine pageCopyEngine,
            EditPlanFactory planFactory,
            EditProperties properties,
            WatermarkImagePreparer imagePreparer,
            EditRenderer renderer) {
        this.pageCopyEngine = pageCopyEngine;
        this.planFactory = planFactory;
        this.properties = properties;
        this.imagePreparer = imagePreparer;
        this.renderer = renderer;
    }

    @Override
    public String key() {
        return "edit";
    }

    @Override
    public void validateSubmission(OperationSubmission submission) {
        if (submission.files().isEmpty()
                || submission.files().size()
                    > properties.getMaxImages() + 1) {
            throw new OperationException(
                "INVALID_FILE_COUNT",
                "Edit PDF requires one PDF and at most "
                    + properties.getMaxImages()
                    + " images"
            );
        }
        PdfOperationValidation.requireSinglePdf(
            new OperationSubmission(
                submission.options(),
                List.of(submission.files().getFirst())
            ),
            "Edit PDF"
        );
        for (int index = 1; index < submission.files().size(); index++) {
            validateImage(submission.files().get(index));
        }
        planFactory.validateShape(
            submission.options(),
            submission.files().size() - 1
        );
        PdfOperationValidation.validateOptionalOutputFilename(
            submission.options(),
            ".pdf",
            MAX_OUTPUT_FILENAME_BYTES,
            "Edit PDF"
        );
    }

    @Override
    public List<OperationOutput> execute(OperationContext context) {
        AtomicReference<EditPlanFactory.EditPlan> plan =
            new AtomicReference<>();
        EditImageProvider imageProvider = new EditImageProvider(
            context.inputs().subList(1, context.inputs().size()),
            context.workspace(),
            imagePreparer,
            properties
        );
        Path output = pageCopyEngine.copySelectedPages(
                context.inputs().getFirst().path(),
                context.workspace(),
                pageCount -> {
                    plan.set(planFactory.create(
                        context.options(),
                        pageCount,
                        context.inputs().size() - 1
                    ));
                    List<Integer> pages = new ArrayList<>(pageCount);
                    for (int page = 1; page <= pageCount; page++) {
                        pages.add(page);
                    }
                    return List.copyOf(pages);
                },
                (document, page, sourcePageNumber, outputPosition) ->
                    renderer.render(
                        document,
                        page,
                        plan.get().forPage(sourcePageNumber),
                        imageProvider,
                        context::checkCancelled
                    ),
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

    private void validateImage(
            OperationSubmission.UploadDescriptor image) {
        String filename = image.filename().toLowerCase(Locale.ROOT);
        boolean extension = filename.endsWith(".png")
            || filename.endsWith(".jpg")
            || filename.endsWith(".jpeg");
        if (!extension
                || !IMAGE_MEDIA_TYPES.contains(
                    image.mediaType().toLowerCase(Locale.ROOT))) {
            throw new OperationException(
                "INVALID_EDIT_IMAGE",
                "Edit images must be PNG or JPEG"
            );
        }
        if (image.sizeBytes() > properties.getMaxImageBytes()) {
            throw new OperationException(
                "EDIT_IMAGE_SIZE_LIMIT_EXCEEDED",
                "An edit image exceeds the configured byte limit"
            );
        }
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
                "Edit PDF"
            );
            return FilenameSanitizer.sanitize(requested, "edited.pdf");
        }
        return FilenameSanitizer.withSuffix(sourceFilename, "_edited");
    }
}
