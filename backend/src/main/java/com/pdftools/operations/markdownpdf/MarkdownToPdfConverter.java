package com.pdftools.operations.markdownpdf;

import com.pdftools.operations.OperationException;
import com.pdftools.operations.OperationInput;
import com.pdftools.operations.htmlpdf.HtmlConversionQueueClient;
import com.pdftools.operations.htmlpdf.HtmlPlanFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.IntConsumer;

@Component
public class MarkdownToPdfConverter {

    private static final int RENDERED_PROGRESS = 25;

    private final MarkdownToHtmlEngine engine;
    private final HtmlPlanFactory planFactory;
    private final HtmlConversionQueueClient queueClient;

    public MarkdownToPdfConverter(
            MarkdownToHtmlEngine engine,
            HtmlPlanFactory planFactory,
            HtmlConversionQueueClient queueClient) {
        this.engine = engine;
        this.planFactory = planFactory;
        this.queueClient = queueClient;
    }

    public Path convert(
            OperationInput input,
            JsonNode options,
            Path workspace,
            IntConsumer progress,
            Runnable cancellationCheck) {
        HtmlPlanFactory.HtmlPlan plan = planFactory.create(options);
        progress.accept(5);
        Path html = engine.render(input, workspace, cancellationCheck);
        progress.accept(RENDERED_PROGRESS);
        OperationInput rendered;
        try {
            rendered = new OperationInput(
                input.position(),
                html,
                "document.html",
                "text/html",
                Files.size(html),
                input.sha256()
            );
        } catch (IOException exception) {
            throw new OperationException(
                "INVALID_MARKDOWN_HTML_OUTPUT",
                "The rendered Markdown HTML is invalid",
                exception
            );
        }
        int[] reported = {RENDERED_PROGRESS};
        return queueClient.convert(
            rendered,
            plan,
            workspace,
            value -> {
                int mapped = RENDERED_PROGRESS
                    + value * (99 - RENDERED_PROGRESS) / 100;
                if (mapped > reported[0]) {
                    reported[0] = mapped;
                    progress.accept(mapped);
                }
            },
            cancellationCheck
        );
    }
}
