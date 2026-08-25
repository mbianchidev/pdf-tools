package com.pdftools.operations.shared.queue;

import java.nio.file.Path;
import java.time.Duration;

public interface ConversionQueueProperties {

    String getQueueCodePrefix();

    String getQueueLabel();

    String getMode();

    Path getQueueRequestRoot();

    Path getQueueResponseRoot();

    Path getQueueSignalRoot();

    Duration getQueueWaitTimeout();

    Duration getQueueRetention();

    Duration getWallTimeout();

    long getMaxOutputBytes();
}
