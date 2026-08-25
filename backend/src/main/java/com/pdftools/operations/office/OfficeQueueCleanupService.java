package com.pdftools.operations.office;

import com.pdftools.operations.OperationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OfficeQueueCleanupService {

    private static final Logger logger =
        LoggerFactory.getLogger(OfficeQueueCleanupService.class);

    private final OfficeConversionQueueClient queueClient;

    public OfficeQueueCleanupService(
            OfficeConversionQueueClient queueClient) {
        this.queueClient = queueClient;
    }

    @Scheduled(
        fixedDelayString =
            "${pdf.operations.office.queue-cleanup-interval:1m}"
    )
    public void cleanup() {
        try {
            queueClient.cleanupStale();
        } catch (OperationException exception) {
            logger.debug(
                "Office queue cleanup skipped because the queue is unavailable",
                exception
            );
        }
    }
}
