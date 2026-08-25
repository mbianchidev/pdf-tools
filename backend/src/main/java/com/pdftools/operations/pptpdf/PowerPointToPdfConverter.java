package com.pdftools.operations.pptpdf;

import com.pdftools.operations.OperationException;
import com.pdftools.operations.OperationInput;
import com.pdftools.operations.office.OfficeConversionProperties;
import com.pdftools.operations.office.OfficeConversionQueueClient;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.function.IntConsumer;

@Component
public class PowerPointToPdfConverter {

    private final OfficeConversionProperties properties;
    private final LibreOfficePowerPointConverter directConverter;
    private final OfficeConversionQueueClient queueClient;

    public PowerPointToPdfConverter(
            OfficeConversionProperties properties,
            LibreOfficePowerPointConverter directConverter,
            OfficeConversionQueueClient queueClient) {
        this.properties = properties;
        this.directConverter = directConverter;
        this.queueClient = queueClient;
    }

    public Path convert(
            OperationInput input,
            Path workspace,
            IntConsumer progress,
            Runnable cancellationCheck) {
        return switch (properties.getMode()) {
            case "queue" -> queueClient.convertPowerPoint(
                input,
                workspace,
                progress,
                cancellationCheck
            );
            case "direct" -> directConverter.convert(
                input,
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
