package com.pdftools.operations.htmlpdf;

import com.pdftools.operations.OperationInput;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.nio.file.Path;
import java.util.function.IntConsumer;

@Component
public class HtmlToPdfConverter {

    private final HtmlDocumentValidator validator;
    private final HtmlPlanFactory planFactory;
    private final HtmlConversionQueueClient queueClient;

    public HtmlToPdfConverter(
            HtmlDocumentValidator validator,
            HtmlPlanFactory planFactory,
            HtmlConversionQueueClient queueClient) {
        this.validator = validator;
        this.planFactory = planFactory;
        this.queueClient = queueClient;
    }

    public Path convert(
            OperationInput input,
            JsonNode options,
            Path workspace,
            IntConsumer progress,
            Runnable cancellationCheck) {
        validator.validate(input.path(), cancellationCheck);
        return queueClient.convert(
            input,
            planFactory.create(options),
            workspace,
            progress,
            cancellationCheck
        );
    }
}
