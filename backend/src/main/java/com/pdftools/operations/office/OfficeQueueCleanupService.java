package com.pdftools.operations.office;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OfficeQueueCleanupService {

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
        queueClient.cleanupStale();
    }
}
