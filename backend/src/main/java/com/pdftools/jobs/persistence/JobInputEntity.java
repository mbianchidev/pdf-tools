package com.pdftools.jobs.persistence;

import com.pdftools.storage.StoredObject;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pdf_job_inputs")
public class JobInputEntity {

    @Id
    private UUID id;

    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Column(nullable = false)
    private int position;

    @Column(name = "storage_key", nullable = false)
    private String storageKey;

    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    @Column(name = "media_type", nullable = false)
    private String mediaType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(nullable = false, length = 64)
    private String sha256;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected JobInputEntity() {
    }

    public JobInputEntity(
            UUID jobId,
            int position,
            String originalFilename,
            StoredObject storedObject,
            Instant createdAt) {
        this.id = UUID.randomUUID();
        this.jobId = jobId;
        this.position = position;
        this.originalFilename = originalFilename;
        this.storageKey = storedObject.key();
        this.mediaType = storedObject.mediaType();
        this.sizeBytes = storedObject.sizeBytes();
        this.sha256 = storedObject.sha256();
        this.createdAt = createdAt;
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

    public String getOriginalFilename() {
        return originalFilename;
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
}
