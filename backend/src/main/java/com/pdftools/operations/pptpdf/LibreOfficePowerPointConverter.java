package com.pdftools.operations.pptpdf;

import com.pdftools.operations.OperationInput;
import com.pdftools.operations.office.LibreOfficeConverter;
import com.pdftools.operations.office.OfficeDocumentType;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.function.IntConsumer;

@Component
public class LibreOfficePowerPointConverter {

    private final PowerPointDocumentValidator validator;
    private final LibreOfficeConverter converter;

    public LibreOfficePowerPointConverter(
            PowerPointDocumentValidator validator,
            LibreOfficeConverter converter) {
        this.validator = validator;
        this.converter = converter;
    }

    public Path convert(
            OperationInput input,
            Path workspace,
            IntConsumer progress,
            Runnable cancellationCheck) {
        validator.validate(
            input.path(),
            input.originalFilename(),
            cancellationCheck
        );
        return converter.convert(
            input,
            workspace,
            OfficeDocumentType.POWERPOINT,
            progress,
            cancellationCheck
        );
    }
}
