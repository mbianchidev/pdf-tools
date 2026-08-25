package com.pdftools.operations.wordpdf;

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
public class WordDocumentValidator {

    private static final Set<String> WORD_MEDIA_TYPES = Set.of(
        "application/msword",
        "application/vnd.openxmlformats-officedocument."
            + "wordprocessingml.document",
        "application/octet-stream"
    );
    private static final OfficeDocumentValidator.Profile PROFILE =
        new OfficeDocumentValidator.Profile(
            "Word document",
            "word/document.xml",
            "word/vbaProject.bin",
            List.of("WordDocument"),
            "INVALID_WORD_DOCUMENT",
            "WORD_EXPANDED_SIZE_LIMIT_EXCEEDED",
            "WORD_MACROS_NOT_SUPPORTED"
        );

    private final OfficeConversionProperties properties;
    private final OfficeDocumentValidator documentValidator;

    public WordDocumentValidator(
            OfficeConversionProperties properties) {
        this.properties = properties;
        this.documentValidator = new OfficeDocumentValidator(properties);
    }

    public void validateSubmission(OperationSubmission submission) {
        if (submission.files().size() != 1) {
            throw new OperationException(
                "INVALID_FILE_COUNT",
                "Word to PDF requires exactly one Word document"
            );
        }
        OperationSubmission.UploadDescriptor file =
            submission.files().getFirst();
        String lowerName = file.filename().toLowerCase(Locale.ROOT);
        if ((!lowerName.endsWith(".docx") && !lowerName.endsWith(".doc"))
                || !WORD_MEDIA_TYPES.contains(
                    file.mediaType().toLowerCase(Locale.ROOT))) {
            throw new OperationException(
                "INVALID_WORD_FILE",
                "Word to PDF accepts DOCX and DOC files"
            );
        }
        if (file.sizeBytes() < 1
                || file.sizeBytes() > properties.getMaxInputBytes()) {
            throw new OperationException(
                "WORD_INPUT_SIZE_LIMIT_EXCEEDED",
                "The Word document exceeds the configured input limit"
            );
        }
    }

    public void validate(
            Path source,
            String originalFilename,
            Runnable cancellationCheck) {
        String lowerName = originalFilename.toLowerCase(Locale.ROOT);
        if (lowerName.endsWith(".docx")) {
            documentValidator.validateOoxml(
                source,
                PROFILE,
                cancellationCheck
            );
            return;
        }
        if (lowerName.endsWith(".doc")) {
            documentValidator.validateOle(source, PROFILE);
            return;
        }
        throw new OperationException(
            "INVALID_WORD_DOCUMENT",
            "The file is not a valid Word document"
        );
    }
}
