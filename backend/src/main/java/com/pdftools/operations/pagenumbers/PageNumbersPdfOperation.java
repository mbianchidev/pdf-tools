package com.pdftools.operations.pagenumbers;

import com.pdftools.operations.OperationContext;
import com.pdftools.operations.OperationException;
import com.pdftools.operations.OperationOutput;
import com.pdftools.operations.OperationSubmission;
import com.pdftools.operations.PdfOperation;
import com.pdftools.operations.PdfOperationValidation;
import com.pdftools.operations.split.PdfSplitEngine;
import com.pdftools.util.FilenameSanitizer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.util.Matrix;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class PageNumbersPdfOperation implements PdfOperation {

    private static final int MAX_OUTPUT_FILENAME_BYTES = 120;

    private final PdfSplitEngine pageCopyEngine;
    private final PageNumbersPlanFactory planFactory;

    public PageNumbersPdfOperation(
            PdfSplitEngine pageCopyEngine,
            PageNumbersPlanFactory planFactory) {
        this.pageCopyEngine = pageCopyEngine;
        this.planFactory = planFactory;
    }

    @Override
    public String key() {
        return "page-numbers";
    }

    @Override
    public void validateSubmission(OperationSubmission submission) {
        PdfOperationValidation.requireSinglePdf(
            submission,
            "Add Page Numbers"
        );
        planFactory.validateShape(submission.options());
        PdfOperationValidation.validateOptionalOutputFilename(
            submission.options(),
            ".pdf",
            MAX_OUTPUT_FILENAME_BYTES,
            "Add Page Numbers"
        );
    }

    @Override
    public List<OperationOutput> execute(OperationContext context) {
        AtomicReference<PageNumbersPlanFactory.PageNumbersPlan> plan =
            new AtomicReference<>();
        AtomicReference<PDType1Font> font = new AtomicReference<>();
        Path output = pageCopyEngine.copySelectedPages(
            context.inputs().getFirst().path(),
            context.workspace(),
            pageCount -> {
                PageNumbersPlanFactory.PageNumbersPlan resolved =
                    planFactory.create(context.options(), pageCount);
                plan.set(resolved);
                font.set(new PDType1Font(resolved.font()));
                List<Integer> pages = new ArrayList<>(pageCount);
                for (int page = 1; page <= pageCount; page++) {
                    pages.add(page);
                }
                return List.copyOf(pages);
            },
            (document, page, sourcePageNumber, outputPosition) -> {
                String text = plan.get().textFor(sourcePageNumber);
                if (text != null) {
                    drawPageNumber(
                        document,
                        page,
                        text,
                        font.get(),
                        plan.get()
                    );
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

    private void drawPageNumber(
            PDDocument document,
            PDPage page,
            String text,
            PDType1Font font,
            PageNumbersPlanFactory.PageNumbersPlan plan) {
        try {
            float userUnit = page.getUserUnit();
            if (!Float.isFinite(userUnit) || userUnit <= 0) {
                throw new OperationException(
                    "INVALID_PAGE_USER_UNIT",
                    "Page UserUnit must be a positive finite number"
                );
            }
            float fontSize = plan.fontSize() / userUnit;
            float margin = plan.margin() / userUnit;
            float textWidth = font.getStringWidth(text)
                / 1000f
                * fontSize;
            PDRectangle box = page.getCropBox();
            int rotation = Math.floorMod(page.getRotation(), 360);
            float visualWidth = rotation % 180 == 0
                ? box.getWidth()
                : box.getHeight();
            float visualHeight = rotation % 180 == 0
                ? box.getHeight()
                : box.getWidth();
            float visualX = horizontalPosition(
                plan.position(),
                visualWidth,
                textWidth,
                margin
            );
            float visualY = plan.position().startsWith("top-")
                ? margin + fontSize
                : visualHeight - margin;
            Point point = toPdfPoint(
                box,
                rotation,
                visualX,
                visualY
            );
            try (PDPageContentStream content = new PDPageContentStream(
                    document,
                    page,
                    PDPageContentStream.AppendMode.APPEND,
                    true,
                    true)) {
                content.beginText();
                content.setFont(font, fontSize);
                content.setTextMatrix(Matrix.getRotateInstance(
                    Math.toRadians(rotation),
                    point.x(),
                    point.y()
                ));
                content.showText(text);
                content.endText();
            }
        } catch (IOException | IllegalArgumentException exception) {
            throw new OperationException(
                "PAGE_NUMBER_RENDER_FAILED",
                "Page number text could not be rendered",
                exception
            );
        }
    }

    private float horizontalPosition(
            String position,
            float visualWidth,
            float textWidth,
            float margin) {
        if (position.endsWith("-left")) {
            return margin;
        }
        if (position.endsWith("-right")) {
            return visualWidth - margin - textWidth;
        }
        return (visualWidth - textWidth) / 2f;
    }

    private Point toPdfPoint(
            PDRectangle box,
            int rotation,
            float visualX,
            float visualY) {
        return switch (rotation) {
            case 0 -> new Point(
                box.getLowerLeftX() + visualX,
                box.getLowerLeftY() + box.getHeight() - visualY
            );
            case 90 -> new Point(
                box.getLowerLeftX() + visualY,
                box.getLowerLeftY() + visualX
            );
            case 180 -> new Point(
                box.getLowerLeftX() + box.getWidth() - visualX,
                box.getLowerLeftY() + visualY
            );
            case 270 -> new Point(
                box.getLowerLeftX() + box.getWidth() - visualY,
                box.getLowerLeftY() + box.getHeight() - visualX
            );
            default -> throw new OperationException(
                "UNSUPPORTED_PAGE_ROTATION",
                "Page rotation must be a multiple of 90 degrees"
            );
        };
    }

    private String outputFilename(JsonNode options, String sourceFilename) {
        String requested = options.path("outputFilename").asText("").trim();
        if (!requested.isEmpty()) {
            PdfOperationValidation.validateOutputFilename(
                requested,
                ".pdf",
                MAX_OUTPUT_FILENAME_BYTES,
                "Add Page Numbers"
            );
            return FilenameSanitizer.sanitize(requested, "numbered.pdf");
        }
        return FilenameSanitizer.withSuffix(sourceFilename, "_numbered");
    }

    private record Point(float x, float y) {
    }
}
