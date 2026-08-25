package com.pdftools.operations.pdfa;

import com.pdftools.operations.OperationInput;
import com.pdftools.operations.office.LibreOfficeConverter;
import com.pdftools.operations.office.OfficeDocumentType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.nio.file.Path;
import java.util.function.IntConsumer;

@Component
public class LibreOfficePdfAConverter {

    private final PdfAPlanFactory planFactory;
    private final LibreOfficeConverter converter;

    public LibreOfficePdfAConverter(
            PdfAPlanFactory planFactory,
            LibreOfficeConverter converter) {
        this.planFactory = planFactory;
        this.converter = converter;
    }

    public Path convert(
            OperationInput input,
            JsonNode options,
            Path workspace,
            IntConsumer progress,
            Runnable cancellationCheck) {
        PdfAPlanFactory.PdfAPlan plan = planFactory.create(options);
        return converter.convert(
            input,
            workspace,
            OfficeDocumentType.PDFA,
            plan.profile().exportFilter(),
            progress,
            cancellationCheck
        );
    }
}
