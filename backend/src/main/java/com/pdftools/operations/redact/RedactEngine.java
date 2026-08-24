package com.pdftools.operations.redact;

import com.pdftools.operations.OperationException;
import com.pdftools.operations.shared.worker.IsolatedJavaWorker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.function.IntConsumer;

@Component
public class RedactEngine {

    private static final Logger logger =
        LoggerFactory.getLogger(RedactEngine.class);

    private final RedactPlanFactory planFactory;
    private final RedactProperties properties;

    public RedactEngine(
            RedactPlanFactory planFactory,
            RedactProperties properties) {
        this.planFactory = planFactory;
        this.properties = properties;
    }

    public Path redact(
            Path source,
            String sourceSha256,
            JsonNode options,
            Path workspace,
            IntConsumer progress,
            Runnable cancellationCheck) {
        IsolatedJavaWorker.Spec workerSpec = workerSpec();
        RedactPlanFactory.RedactPlan plan = planFactory.parse(options);
        Path output = workspace.resolve("redacted.pdf");
        Path requestFile = workspace.resolve(".redact-request.bin");
        Path progressFile = workspace.resolve(".redact-progress");
        Path errorFile = workspace.resolve(".redact-error");
        RuntimeException failure = null;
        boolean success = false;
        try {
            RedactWorkerRequest.write(
                requestFile,
                source,
                output,
                sourceSha256,
                plan,
                properties
            );
            progress.accept(3);
            int[] reported = {0};
            int exitCode = IsolatedJavaWorker.run(
                workerSpec,
                List.of(
                    requestFile.toAbsolutePath().toString(),
                    progressFile.toAbsolutePath().toString(),
                    errorFile.toAbsolutePath().toString()
                ),
                cancellationCheck,
                () -> updateProgress(progressFile, reported, progress)
            );
            if (exitCode != 0) {
                throw IsolatedJavaWorker.readFailure(
                    exitCode,
                    errorFile,
                    "REDACT_RESOURCE_LIMIT_EXCEEDED",
                    "Secure redaction stopped before completing",
                    logger
                );
            }
            validateOutput(output);
            progress.accept(97);
            success = true;
            return output;
        } catch (RuntimeException exception) {
            failure = exception;
            throw exception;
        } finally {
            cleanup(requestFile, failure);
            cleanup(progressFile, failure);
            cleanup(errorFile, failure);
            if (!success) {
                cleanup(output, failure);
            }
        }
    }

    private void updateProgress(
            Path progressFile,
            int[] reported,
            IntConsumer progress) {
        if (!Files.exists(progressFile, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try {
            int completed = Integer.parseInt(
                Files.readString(progressFile).trim()
            );
            if (completed < 0 || completed > 100) {
                throw protocolFailure();
            }
            if (completed > reported[0]) {
                reported[0] = completed;
                progress.accept(Math.min(
                    5 + (int) Math.floor(completed * 0.9),
                    95
                ));
            }
        } catch (NumberFormatException | IOException exception) {
            throw protocolFailure();
        }
    }

    private void validateOutput(Path output) {
        try {
            if (Files.isSymbolicLink(output)
                    || !Files.isRegularFile(
                        output,
                        LinkOption.NOFOLLOW_LINKS
                    )) {
                throw protocolFailure();
            }
            long size = Files.size(output);
            if (size < 1 || size > properties.getMaxOutputBytes()) {
                throw protocolFailure();
            }
        } catch (IOException exception) {
            throw protocolFailure();
        }
    }

    private IsolatedJavaWorker.Spec workerSpec() {
        return new IsolatedJavaWorker.Spec(
            RedactWorkerMain.class,
            properties.getWorkerHeapBytes(),
            properties.getWorkerTimeout(),
            "REDACT_WORKER_START_FAILED",
            "The isolated secure redaction worker could not be started",
            "REDACT_TIMEOUT",
            "Secure redaction exceeded the configured time limit"
        );
    }

    private void cleanup(Path path, RuntimeException failure) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            OperationException cleanupFailure = new OperationException(
                "REDACT_CLEANUP_FAILED",
                "Secure redaction scratch could not be removed",
                exception
            );
            if (failure != null) {
                failure.addSuppressed(cleanupFailure);
            }
            logger.error(
                "Could not remove secure redaction scratch {}",
                path,
                cleanupFailure
            );
        }
    }

    private OperationException protocolFailure() {
        return new OperationException(
            "REDACT_WORKER_PROTOCOL_ERROR",
            "The secure redaction worker returned an invalid result"
        );
    }
}
