package com.pdftools.operations.htmlpdf;

import com.pdftools.operations.OperationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class HtmlQueueCleanupService {

    private static final Logger logger =
        LoggerFactory.getLogger(HtmlQueueCleanupService.class);

    private final HtmlConversionQueueClient queueClient;

    public HtmlQueueCleanupService(HtmlConversionQueueClient queueClient) {
        this.queueClient = queueClient;
    }

    @Scheduled(
        fixedDelayString =
            "${pdf.operations.html.queue-cleanup-interval:1m}"
    )
    public void cleanup() {
        try {
            queueClient.cleanupStale();
        } catch (OperationException exception) {
            logger.debug(
                "HTML queue cleanup skipped because the queue is unavailable",
                exception
            );
        }
    }
}
