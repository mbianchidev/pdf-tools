package com.pdftools.operations.wordpdf;

import com.pdftools.operations.OperationInput;
import com.pdftools.operations.office.LibreOfficeConverter;
import com.pdftools.operations.office.OfficeDocumentType;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.function.IntConsumer;

@Component
public class LibreOfficeWordConverter {

    private final WordDocumentValidator validator;
    private final LibreOfficeConverter converter;

    public LibreOfficeWordConverter(
            WordDocumentValidator validator,
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
            OfficeDocumentType.WORD,
            progress,
            cancellationCheck
        );
    }
}
