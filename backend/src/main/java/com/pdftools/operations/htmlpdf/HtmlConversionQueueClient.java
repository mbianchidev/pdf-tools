package com.pdftools.operations.htmlpdf;

import com.pdftools.operations.OperationInput;
import com.pdftools.operations.office.OfficeConversionQueueClient;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.function.IntConsumer;

@Component
public class HtmlConversionQueueClient {

    private final OfficeConversionQueueClient delegate;
    private final HtmlDocumentType documentType;

    public HtmlConversionQueueClient(HtmlProperties properties) {
        this.delegate = OfficeConversionQueueClient.using(properties);
        this.documentType = new HtmlDocumentType(properties.getMaxPages());
    }

    public Path convert(
            OperationInput input,
            HtmlPlanFactory.HtmlPlan plan,
            Path workspace,
            IntConsumer progress,
            Runnable cancellationCheck) {
        return delegate.convertQueued(
            input,
            workspace,
            documentType,
            optionsJson(plan),
            progress,
            cancellationCheck
        );
    }

    public void cleanupStale() {
        delegate.cleanupStale();
    }

    private String optionsJson(HtmlPlanFactory.HtmlPlan plan) {
        return "{\"pageSize\":\"" + plan.pageSize()
            + "\",\"landscape\":" + plan.landscape()
            + ",\"printBackground\":" + plan.printBackground()
            + ",\"marginMm\":" + plan.marginMm() + "}";
    }
}
