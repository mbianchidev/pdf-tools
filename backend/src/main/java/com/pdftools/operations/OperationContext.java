package com.pdftools.operations;

import tools.jackson.databind.JsonNode;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;

public final class OperationContext {

    private final UUID jobId;
    private final JsonNode options;
    private final List<OperationInput> inputs;
    private final Path workspace;
    private final IntConsumer progressReporter;
    private final BooleanSupplier cancellationRequested;

    public OperationContext(
            UUID jobId,
            JsonNode options,
            List<OperationInput> inputs,
            Path workspace,
            IntConsumer progressReporter,
            BooleanSupplier cancellationRequested) {
        this.jobId = jobId;
        this.options = options;
        this.inputs = List.copyOf(inputs);
        this.workspace = workspace;
        this.progressReporter = progressReporter;
        this.cancellationRequested = cancellationRequested;
    }

    public UUID jobId() {
        return jobId;
    }

    public JsonNode options() {
        return options;
    }

    public List<OperationInput> inputs() {
        return inputs;
    }

    public Path workspace() {
        return workspace;
    }

    public void reportProgress(int progress) {
        if (progress < 0 || progress > 99) {
            throw new IllegalArgumentException("Operation progress must be between 0 and 99");
        }
        checkCancelled();
        progressReporter.accept(progress);
    }

    public void checkCancelled() {
        if (cancellationRequested.getAsBoolean()) {
            throw new OperationCancelledException();
        }
    }
}
