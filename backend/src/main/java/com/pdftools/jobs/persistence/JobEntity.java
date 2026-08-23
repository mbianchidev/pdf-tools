package com.pdftools.jobs.persistence;

import com.pdftools.jobs.JobStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "pdf_jobs")
public class JobEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 64)
    private String operation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private JobStatus status;

    @Column(nullable = false)
    private int progress;

    private String message;

    @Column(name = "error_code", length = 96)
    private String errorCode;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "options_json", nullable = false)
    private String optionsJson;

    @Column(name = "cancel_requested", nullable = false)
    private boolean cancelRequested;

    @Column(name = "worker_id", length = 64)
    private String workerId;

    @Column(name = "lease_expires_at")
    private Instant leaseExpiresAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected JobEntity() {
    }

    private JobEntity(
            UUID id,
            String operation,
            String optionsJson,
            Instant createdAt,
            Instant expiresAt) {
        this.id = id;
        this.operation = operation;
        this.optionsJson = optionsJson;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
        this.expiresAt = expiresAt;
        this.status = JobStatus.PENDING;
        this.progress = 0;
        this.message = "Queued";
    }

    public static JobEntity pending(
            String operation,
            String optionsJson,
            Instant createdAt,
            Instant expiresAt) {
        return new JobEntity(UUID.randomUUID(), operation, optionsJson, createdAt, expiresAt);
    }

    public static JobEntity uploading(
            String operation,
            String optionsJson,
            Instant createdAt,
            Instant expiresAt) {
        JobEntity job = pending(operation, optionsJson, createdAt, expiresAt);
        job.status = JobStatus.UPLOADING;
        job.message = "Uploading";
        return job;
    }

    public void queue(Instant now) {
        requireStatus(JobStatus.UPLOADING);
        status = JobStatus.PENDING;
        message = "Queued";
        workerId = null;
        leaseExpiresAt = null;
        updatedAt = now;
    }

    public void start(Instant now) {
        start(now, null, null);
    }

    public void start(Instant now, String nextWorkerId, Instant nextLeaseExpiration) {
        requireStatus(JobStatus.PENDING);
        status = JobStatus.RUNNING;
        progress = 1;
        message = "Processing";
        workerId = nextWorkerId;
        leaseExpiresAt = nextLeaseExpiration;
        updatedAt = now;
    }

    public void renewLease(String expectedWorkerId, Instant nextLeaseExpiration) {
        requireStatus(JobStatus.RUNNING);
        if (!Objects.equals(workerId, expectedWorkerId)) {
            throw new IllegalStateException("The job is owned by another worker");
        }
        leaseExpiresAt = nextLeaseExpiration;
    }

    public void requestCancellation(Instant now) {
        if (status.isTerminal()) {
            throw new IllegalStateException("Terminal jobs cannot be cancelled");
        }
        cancelRequested = true;
        message = status == JobStatus.PENDING ? "Cancellation requested" : "Cancelling";
        updatedAt = now;
    }

    public void cancel(Instant now, Instant expiration) {
        status = JobStatus.CANCELLED;
        progress = Math.min(progress, 99);
        message = "Cancelled";
        errorCode = null;
        errorMessage = null;
        workerId = null;
        leaseExpiresAt = null;
        expiresAt = expiration;
        updatedAt = now;
    }

    public void updateProgress(int nextProgress, String nextMessage, Instant now) {
        requireStatus(JobStatus.RUNNING);
        if (nextProgress < progress || nextProgress > 99) {
            return;
        }
        progress = nextProgress;
        message = nextMessage;
        updatedAt = now;
    }

    public void complete(Instant now, Instant expiration) {
        requireStatus(JobStatus.RUNNING);
        status = JobStatus.COMPLETED;
        progress = 100;
        message = "Completed";
        workerId = null;
        leaseExpiresAt = null;
        expiresAt = expiration;
        updatedAt = now;
    }

    public void fail(String code, String failureMessage, Instant now, Instant expiration) {
        if (status.isTerminal()) {
            return;
        }
        status = JobStatus.FAILED;
        message = "Failed";
        errorCode = code;
        errorMessage = failureMessage;
        workerId = null;
        leaseExpiresAt = null;
        expiresAt = expiration;
        updatedAt = now;
    }

    private void requireStatus(JobStatus expected) {
        if (status != expected) {
            throw new IllegalStateException(
                "Expected job status " + expected + " but was " + status
            );
        }
    }

    public UUID getId() {
        return id;
    }

    public String getOperation() {
        return operation;
    }

    public JobStatus getStatus() {
        return status;
    }

    public int getProgress() {
        return progress;
    }

    public String getMessage() {
        return message;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getOptionsJson() {
        return optionsJson;
    }

    public boolean isCancelRequested() {
        return cancelRequested;
    }

    public String getWorkerId() {
        return workerId;
    }

    public Instant getLeaseExpiresAt() {
        return leaseExpiresAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }
}
