package com.pdftools.operations.excelpdf;

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
public class ExcelDocumentValidator {

    private static final Set<String> EXCEL_MEDIA_TYPES = Set.of(
        "application/vnd.ms-excel",
        "application/vnd.openxmlformats-officedocument."
            + "spreadsheetml.sheet",
        "application/octet-stream"
    );
    private static final OfficeDocumentValidator.Profile PROFILE =
        new OfficeDocumentValidator.Profile(
            "Excel workbook",
            "xl/workbook.xml",
            "xl/vbaProject.bin",
            List.of("Workbook", "Book"),
            "INVALID_EXCEL_DOCUMENT",
            "EXCEL_EXPANDED_SIZE_LIMIT_EXCEEDED",
            "EXCEL_MACROS_NOT_SUPPORTED"
        );

    private final OfficeConversionProperties properties;
    private final OfficeDocumentValidator documentValidator;

    public ExcelDocumentValidator(
            OfficeConversionProperties properties) {
        this.properties = properties;
        this.documentValidator = new OfficeDocumentValidator(properties);
    }

    public void validateSubmission(OperationSubmission submission) {
        if (submission.files().size() != 1) {
            throw new OperationException(
                "INVALID_FILE_COUNT",
                "Excel to PDF requires exactly one workbook"
            );
        }
        OperationSubmission.UploadDescriptor file =
            submission.files().getFirst();
        String lowerName = file.filename().toLowerCase(Locale.ROOT);
        if ((!lowerName.endsWith(".xlsx") && !lowerName.endsWith(".xls"))
                || !EXCEL_MEDIA_TYPES.contains(
                    file.mediaType().toLowerCase(Locale.ROOT))) {
            throw new OperationException(
                "INVALID_EXCEL_FILE",
                "Excel to PDF accepts XLSX and XLS files"
            );
        }
        if (file.sizeBytes() < 1
                || file.sizeBytes() > properties.getMaxInputBytes()) {
            throw new OperationException(
                "EXCEL_INPUT_SIZE_LIMIT_EXCEEDED",
                "The workbook exceeds the configured input limit"
            );
        }
    }

    public void validate(
            Path source,
            String originalFilename,
            Runnable cancellationCheck) {
        String lowerName = originalFilename.toLowerCase(Locale.ROOT);
        if (lowerName.endsWith(".xlsx")) {
            if (documentValidator.isEncryptedOoxml(source)) {
                throw new OperationException(
                    "ENCRYPTED_EXCEL_DOCUMENT",
                    "Password-protected Excel workbooks are not supported"
                );
            }
            documentValidator.validateOoxml(
                source,
                PROFILE,
                cancellationCheck
            );
            return;
        }
        if (lowerName.endsWith(".xls")) {
            documentValidator.validateOle(source, PROFILE);
            return;
        }
        throw new OperationException(
            "INVALID_EXCEL_DOCUMENT",
            "The file is not a valid Excel workbook"
        );
    }
}
