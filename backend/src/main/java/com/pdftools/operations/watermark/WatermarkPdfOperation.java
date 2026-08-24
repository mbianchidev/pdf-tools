package com.pdftools.operations.watermark;

import com.pdftools.operations.OperationContext;
import com.pdftools.operations.OperationException;
import com.pdftools.operations.OperationOutput;
import com.pdftools.operations.OperationSubmission;
import com.pdftools.operations.PdfOperation;
import com.pdftools.operations.PdfOperationValidation;
import com.pdftools.operations.split.PdfSplitEngine;
import com.pdftools.util.FilenameSanitizer;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class WatermarkPdfOperation implements PdfOperation {

    private static final int MAX_OUTPUT_FILENAME_BYTES = 120;
    private static final Set<String> IMAGE_MEDIA_TYPES = Set.of(
        "image/png",
        "image/jpeg",
        "image/jpg",
        "application/octet-stream"
    );

    private final PdfSplitEngine pageCopyEngine;
    private final WatermarkPlanFactory planFactory;
    private final WatermarkImagePreparer imagePreparer;
    private final WatermarkRenderer renderer;
    private final WatermarkProperties properties;

    public WatermarkPdfOperation(
            PdfSplitEngine pageCopyEngine,
            WatermarkPlanFactory planFactory,
            WatermarkImagePreparer imagePreparer,
            WatermarkRenderer renderer,
            WatermarkProperties properties) {
        this.pageCopyEngine = pageCopyEngine;
        this.planFactory = planFactory;
        this.imagePreparer = imagePreparer;
        this.renderer = renderer;
        this.properties = properties;
    }

    @Override
    public String key() {
        return "watermark";
    }

    @Override
    public void validateSubmission(OperationSubmission submission) {
        WatermarkPlanFactory.WatermarkMode mode =
            planFactory.mode(submission.options());
        int expectedFiles = mode
            == WatermarkPlanFactory.WatermarkMode.TEXT ? 1 : 2;
        if (submission.files().size() != expectedFiles) {
            throw new OperationException(
                "INVALID_FILE_COUNT",
                mode == WatermarkPlanFactory.WatermarkMode.TEXT
                    ? "Text watermark requires exactly one PDF"
                    : "Image watermark requires one PDF and one image"
            );
        }
        PdfOperationValidation.requireSinglePdf(
            new OperationSubmission(
                submission.options(),
                List.of(submission.files().getFirst())
            ),
            "Watermark"
        );
        if (mode == WatermarkPlanFactory.WatermarkMode.IMAGE) {
            validateImage(submission.files().get(1));
        }
        planFactory.validateShape(submission.options());
        PdfOperationValidation.validateOptionalOutputFilename(
            submission.options(),
            ".pdf",
            MAX_OUTPUT_FILENAME_BYTES,
            "Watermark"
        );
    }

    @Override
    public List<OperationOutput> execute(OperationContext context) {
        WatermarkPlanFactory.WatermarkMode mode =
            planFactory.mode(context.options());
        try (WatermarkImagePreparer.PreparedImage prepared =
                mode == WatermarkPlanFactory.WatermarkMode.IMAGE
                    ? imagePreparer.prepare(
                        context.inputs().get(1),
                        context.workspace(),
                        context::checkCancelled
                    )
                    : null) {
            AtomicReference<WatermarkPlanFactory.WatermarkPlan> plan =
                new AtomicReference<>();
            AtomicReference<PDType1Font> font = new AtomicReference<>();
            AtomicReference<PDImageXObject> image =
                new AtomicReference<>();
            Path output = pageCopyEngine.copySelectedPages(
                context.inputs().getFirst().path(),
                context.workspace(),
                pageCount -> {
                    WatermarkPlanFactory.WatermarkPlan resolved =
                        planFactory.create(context.options(), pageCount);
                    plan.set(resolved);
                    if (resolved.mode()
                            == WatermarkPlanFactory.WatermarkMode.TEXT) {
                        font.set(new PDType1Font(resolved.font()));
                    }
                    List<Integer> pages = new ArrayList<>(pageCount);
                    for (int page = 1; page <= pageCount; page++) {
                        pages.add(page);
                    }
                    return List.copyOf(pages);
                },
                (document, page, sourcePageNumber, outputPosition) -> {
                    WatermarkPlanFactory.WatermarkPlan resolved =
                        plan.get();
                    if (!resolved.includes(sourcePageNumber)) {
                        return;
                    }
                    if (resolved.mode()
                            == WatermarkPlanFactory.WatermarkMode.TEXT) {
                        renderer.drawText(
                            document,
                            page,
                            resolved,
                            font.get()
                        );
                        return;
                    }
                    if (image.get() == null) {
                        try {
                            image.set(prepared.create(
                                document,
                                context::checkCancelled
                            ));
                        } catch (java.io.IOException exception) {
                            throw new OperationException(
                                "WATERMARK_IMAGE_EMBED_FAILED",
                                "The watermark image could not be embedded",
                                exception
                            );
                        }
                    }
                    renderer.drawImage(
                        document,
                        page,
                        resolved,
                        prepared,
                        image.get()
                    );
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
                "INVALID_WATERMARK_IMAGE",
                "Watermark image must be PNG or JPEG"
            );
        }
        if (image.sizeBytes() > properties.getMaxImageBytes()) {
            throw new OperationException(
                "WATERMARK_IMAGE_SIZE_LIMIT_EXCEEDED",
                "Watermark image exceeds the configured byte limit"
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
                "Watermark"
            );
            return FilenameSanitizer.sanitize(
                requested,
                "watermarked.pdf"
            );
        }
        return FilenameSanitizer.withSuffix(
            sourceFilename,
            "_watermarked"
        );
    }
}
