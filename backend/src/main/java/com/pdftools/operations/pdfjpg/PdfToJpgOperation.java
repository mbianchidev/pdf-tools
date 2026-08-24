package com.pdftools.operations.pdfjpg;

import com.pdftools.operations.OperationContext;
import com.pdftools.operations.OperationOutput;
import com.pdftools.operations.OperationSubmission;
import com.pdftools.operations.PdfOperation;
import com.pdftools.operations.PdfOperationValidation;
import com.pdftools.operations.ZipArtifactService;
import com.pdftools.util.FilenameSanitizer;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class PdfToJpgOperation implements PdfOperation {

    private static final int MAX_OUTPUT_FILENAME_BYTES = 120;

    private final PdfToJpgEngine engine;
    private final ZipArtifactService zipArtifactService;
    private final PdfToJpgProperties properties;
    private final PdfToJpgPlanFactory planFactory;

    public PdfToJpgOperation(
            PdfToJpgEngine engine,
            ZipArtifactService zipArtifactService,
            PdfToJpgProperties properties,
            PdfToJpgPlanFactory planFactory) {
        this.engine = engine;
        this.zipArtifactService = zipArtifactService;
        this.properties = properties;
        this.planFactory = planFactory;
    }

    @Override
    public String key() {
        return "pdf-to-jpg";
    }

    @Override
    public void validateSubmission(OperationSubmission submission) {
        PdfOperationValidation.requireSinglePdf(
            submission,
            "PDF to JPG"
        );
        planFactory.validateShape(submission.options());
        PdfOperationValidation.validateOptionalOutputFilename(
            submission.options(),
            ".zip",
            MAX_OUTPUT_FILENAME_BYTES,
            "PDF to JPG"
        );
    }

    @Override
    public List<OperationOutput> execute(OperationContext context) {
        PdfToJpgResult result = engine.render(
            context.inputs().getFirst().path(),
            context.options(),
            context.workspace(),
            context::reportProgress,
            context::checkCancelled
        );
        String sourceFilename =
            context.inputs().getFirst().originalFilename();
        List<OperationOutput> images = new ArrayList<>(
            result.parts().size()
        );
        for (PdfToJpgResult.Part part : result.parts()) {
            images.add(new OperationOutput(
                part.path(),
                imageFilename(sourceFilename, part.pageNumber()),
                "image/jpeg"
            ));
        }
        context.reportProgress(93);
        OperationOutput archive = zipArtifactService.create(
            images,
            context.workspace().resolve("jpg-images.zip"),
            outputFilename(context.options(), sourceFilename),
            properties.getMaxArchiveBytes(),
            context::checkCancelled,
            true
        );
        context.reportProgress(97);
        return List.of(archive);
    }

    private String imageFilename(String sourceFilename, int pageNumber) {
        String numberedPdf = FilenameSanitizer.withSuffix(
            sourceFilename,
            String.format(Locale.ROOT, "_page_%04d", pageNumber)
        );
        return numberedPdf.substring(0, numberedPdf.length() - 4)
            + ".jpg";
    }

    private String outputFilename(
            JsonNode options,
            String sourceFilename) {
        String requested = options.path("outputFilename").asText("").trim();
        if (!requested.isEmpty()) {
            PdfOperationValidation.validateOutputFilename(
                requested,
                ".zip",
                MAX_OUTPUT_FILENAME_BYTES,
                "PDF to JPG"
            );
            return FilenameSanitizer.sanitize(
                requested,
                "jpg-images.zip"
            );
        }
        String pdfName = FilenameSanitizer.withSuffix(
            sourceFilename,
            "_jpg"
        );
        return pdfName.substring(0, pdfName.length() - 4) + ".zip";
    }
}
