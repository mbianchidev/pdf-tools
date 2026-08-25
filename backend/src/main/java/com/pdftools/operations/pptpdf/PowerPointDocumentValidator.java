package com.pdftools.operations.pptpdf;

import com.pdftools.operations.OperationException;
import com.pdftools.operations.OperationSubmission;
import com.pdftools.operations.office.OfficeConversionProperties;
import com.pdftools.operations.office.OfficeDocumentValidator;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class PowerPointDocumentValidator {

    private static final Set<String> POWERPOINT_MEDIA_TYPES = Set.of(
        "application/vnd.ms-powerpoint",
        "application/vnd.openxmlformats-officedocument."
            + "presentationml.presentation",
        "application/octet-stream"
    );
    private static final OfficeDocumentValidator.Profile PROFILE =
        new OfficeDocumentValidator.Profile(
            "PowerPoint presentation",
            "ppt/presentation.xml",
            "ppt/vbaProject.bin",
            List.of("PowerPoint Document"),
            "INVALID_POWERPOINT_DOCUMENT",
            "POWERPOINT_EXPANDED_SIZE_LIMIT_EXCEEDED",
            "POWERPOINT_MACROS_NOT_SUPPORTED"
        );

    private final OfficeConversionProperties properties;
    private final OfficeDocumentValidator documentValidator;

    public PowerPointDocumentValidator(
            OfficeConversionProperties properties) {
        this.properties = properties;
        this.documentValidator = new OfficeDocumentValidator(properties);
    }

    public void validateSubmission(OperationSubmission submission) {
        if (submission.files().size() != 1) {
            throw new OperationException(
                "INVALID_FILE_COUNT",
                "PowerPoint to PDF requires exactly one presentation"
            );
        }
        OperationSubmission.UploadDescriptor file =
            submission.files().getFirst();
        String lowerName = file.filename().toLowerCase(Locale.ROOT);
        if ((!lowerName.endsWith(".pptx") && !lowerName.endsWith(".ppt"))
                || !POWERPOINT_MEDIA_TYPES.contains(
                    file.mediaType().toLowerCase(Locale.ROOT))) {
            throw new OperationException(
                "INVALID_POWERPOINT_FILE",
                "PowerPoint to PDF accepts PPTX and PPT files"
            );
        }
        if (file.sizeBytes() < 1
                || file.sizeBytes() > properties.getMaxInputBytes()) {
            throw new OperationException(
                "POWERPOINT_INPUT_SIZE_LIMIT_EXCEEDED",
                "The presentation exceeds the configured input limit"
            );
        }
    }

    public void validate(
            Path source,
            String originalFilename,
            Runnable cancellationCheck) {
        String lowerName = originalFilename.toLowerCase(Locale.ROOT);
        if (lowerName.endsWith(".pptx")) {
            documentValidator.validateOoxml(
                source,
                PROFILE,
                cancellationCheck
            );
            return;
        }
        if (lowerName.endsWith(".ppt")) {
            documentValidator.validateOle(source, PROFILE);
            return;
        }
        throw new OperationException(
            "INVALID_POWERPOINT_DOCUMENT",
            "The file is not a valid PowerPoint presentation"
        );
    }
}
