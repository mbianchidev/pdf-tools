package com.pdftools.operations.pdfa;

import com.pdftools.operations.OperationException;
import com.pdftools.operations.OperationInput;
import com.pdftools.operations.office.OfficeConversionProperties;
import com.pdftools.operations.office.OfficeConversionQueueClient;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.nio.file.Path;
import java.util.function.IntConsumer;

@Component
public class PdfAConverter {

    private final OfficeConversionProperties properties;
    private final LibreOfficePdfAConverter directConverter;
    private final OfficeConversionQueueClient queueClient;

    public PdfAConverter(
            OfficeConversionProperties properties,
            LibreOfficePdfAConverter directConverter,
            OfficeConversionQueueClient queueClient) {
        this.properties = properties;
        this.directConverter = directConverter;
        this.queueClient = queueClient;
    }

    public Path convert(
            OperationInput input,
            JsonNode options,
            Path workspace,
            IntConsumer progress,
            Runnable cancellationCheck) {
        return switch (properties.getMode()) {
            case "queue" -> queueClient.convertPdfA(
                input,
                workspace,
                options,
                progress,
                cancellationCheck
            );
            case "direct" -> directConverter.convert(
                input,
                options,
                workspace,
                progress,
                cancellationCheck
            );
            default -> throw new OperationException(
                "OFFICE_CONVERSION_MODE_INVALID",
                "Office conversion mode must be queue or direct"
            );
        };
    }
}
