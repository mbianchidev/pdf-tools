package com.pdftools.jobs.persistence;

import com.pdftools.storage.StoredObject;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pdf_job_outputs")
public class JobOutputEntity {

    @Id
    private UUID id;

    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Column(nullable = false)
    private int position;

    @Column(name = "storage_key", nullable = false)
    private String storageKey;

    @Column(nullable = false)
    private String filename;

    @Column(name = "media_type", nullable = false)
    private String mediaType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(nullable = false, length = 64)
    private String sha256;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected JobOutputEntity() {
    }

    public JobOutputEntity(
            UUID jobId,
            int position,
            String filename,
            StoredObject storedObject,
            Instant createdAt,
            Instant expiresAt) {
        this.id = UUID.randomUUID();
        this.jobId = jobId;
        this.position = position;
        this.filename = filename;
        this.storageKey = storedObject.key();
        this.mediaType = storedObject.mediaType();
        this.sizeBytes = storedObject.sizeBytes();
        this.sha256 = storedObject.sha256();
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getJobId() {
        return jobId;
    }

    public int getPosition() {
        return position;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getFilename() {
        return filename;
    }

    public String getMediaType() {
        return mediaType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getSha256() {
        return sha256;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
