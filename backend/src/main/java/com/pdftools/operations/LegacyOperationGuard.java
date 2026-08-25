package com.pdftools.operations;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class LegacyOperationGuard {

    private static final Logger logger =
        LoggerFactory.getLogger(LegacyOperationGuard.class);

    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final AtomicReference<Path> output = new AtomicReference<>();

    public void checkCancelled() {
        if (cancelled.get() || Thread.currentThread().isInterrupted()) {
            throw new OperationCancelledException();
        }
    }

    public void own(Path path) {
        output.set(path);
        if (cancelled.get()) {
            cleanupOutput();
            throw new OperationCancelledException();
        }
    }

    public void cancel() {
        cancelled.set(true);
        cleanupOutput();
    }

    public void complete() {
        if (cancelled.get()) {
            cleanupOutput();
        } else {
            output.set(null);
        }
    }

    private void cleanupOutput() {
        Path path = output.getAndSet(null);
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            output.compareAndSet(null, path);
            logger.warn("Failed to remove cancelled legacy output {}", path, exception);
        }
    }
}
