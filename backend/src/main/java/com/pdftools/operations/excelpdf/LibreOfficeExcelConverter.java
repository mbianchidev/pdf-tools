package com.pdftools.operations.excelpdf;

import com.pdftools.operations.OperationInput;
import com.pdftools.operations.office.LibreOfficeConverter;
import com.pdftools.operations.office.OfficeDocumentType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.IntConsumer;

@Component
public class LibreOfficeExcelConverter {

    private final ExcelDocumentValidator validator;
    private final ExcelPlanFactory planFactory;
    private final ExcelPreparationService preparationService;
    private final LibreOfficeConverter converter;

    public LibreOfficeExcelConverter(
            ExcelDocumentValidator validator,
            ExcelPlanFactory planFactory,
            ExcelPreparationService preparationService,
            LibreOfficeConverter converter) {
        this.validator = validator;
        this.planFactory = planFactory;
        this.preparationService = preparationService;
        this.converter = converter;
    }

    public Path convert(
            OperationInput input,
            JsonNode options,
            Path workspace,
            IntConsumer progress,
            Runnable cancellationCheck) {
        validator.validate(
            input.path(),
            input.originalFilename(),
            cancellationCheck
        );
        ExcelPlanFactory.ExcelPlan plan = planFactory.create(
            options,
            planFactory.spreadsheetVersion(input.originalFilename())
        );
        String extension = OfficeDocumentType.EXCEL.extension(
            input.originalFilename()
        );
        Path prepared = preparationService.prepare(
            input.path(),
            workspace.resolve("prepared" + extension),
            plan,
            planFactory.spreadsheetVersion(input.originalFilename()),
            workspace,
            cancellationCheck
        );
        progress.accept(2);
        try {
            OperationInput preparedInput = new OperationInput(
                1,
                prepared,
                "source" + extension,
                input.mediaType(),
                Files.size(prepared),
                input.sha256()
            );
            return converter.convert(
                preparedInput,
                workspace,
                OfficeDocumentType.EXCEL,
                progress,
                cancellationCheck
            );
        } catch (java.io.IOException exception) {
            throw new com.pdftools.operations.OperationException(
                "EXCEL_PREPARED_FILE_FAILED",
                "The prepared workbook could not be read",
                exception
            );
        }
    }
}
