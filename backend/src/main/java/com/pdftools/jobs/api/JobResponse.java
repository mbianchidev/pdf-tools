package com.pdftools.jobs.api;

import com.pdftools.jobs.JobStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record JobResponse(
    UUID id,
    String operation,
    JobStatus status,
    long version,
    int progress,
    String message,
    String errorCode,
    String errorMessage,
    boolean cancellationRequested,
    Instant createdAt,
    Instant updatedAt,
    Instant expiresAt,
    List<JobOutputResponse> outputs
) {
    public JobResponse {
        outputs = List.copyOf(outputs);
    }
}
