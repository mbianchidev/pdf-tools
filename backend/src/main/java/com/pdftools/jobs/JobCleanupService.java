package com.pdftools.jobs;

import com.pdftools.config.JobProperties;
import com.pdftools.jobs.persistence.JobEntity;
import com.pdftools.jobs.persistence.JobInputEntity;
import com.pdftools.jobs.persistence.JobInputRepository;
import com.pdftools.jobs.persistence.JobOutputEntity;
import com.pdftools.jobs.persistence.JobOutputRepository;
import com.pdftools.jobs.persistence.JobRepository;
import com.pdftools.storage.StorageService;
import com.pdftools.storage.StoredObjectInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

@Service
public class JobCleanupService {

    private static final Logger logger = LoggerFactory.getLogger(JobCleanupService.class);

    private final JobRepository jobRepository;
    private final JobInputRepository inputRepository;
    private final JobOutputRepository outputRepository;
    private final StorageService storageService;
    private final TransactionTemplate transactionTemplate;
    private final JobProperties properties;

    public JobCleanupService(
            JobRepository jobRepository,
            JobInputRepository inputRepository,
            JobOutputRepository outputRepository,
            StorageService storageService,
            TransactionTemplate transactionTemplate,
            JobProperties properties) {
        this.jobRepository = jobRepository;
        this.inputRepository = inputRepository;
        this.outputRepository = outputRepository;
        this.storageService = storageService;
        this.transactionTemplate = transactionTemplate;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${pdf.jobs.cleanup-interval:15m}")
    public void cleanupExpiredJobs() {
        List<JobEntity> expired = jobRepository.findAllByExpiresAtBeforeAndStatusIn(
            Instant.now(),
            EnumSet.of(JobStatus.COMPLETED, JobStatus.FAILED, JobStatus.CANCELLED)
        );
        for (JobEntity job : expired) {
            cleanup(job);
        }
        cleanupOrphanedObjects();
    }

    private void cleanup(JobEntity job) {
        List<String> keys = new java.util.ArrayList<>();
        inputRepository.findAllByJobIdOrderByPosition(job.getId()).stream()
            .map(JobInputEntity::getStorageKey)
            .forEach(keys::add);
        outputRepository.findAllByJobIdOrderByPosition(job.getId()).stream()
            .map(JobOutputEntity::getStorageKey)
            .forEach(keys::add);

        try {
            for (String key : keys) {
                storageService.delete(key);
            }
            transactionTemplate.executeWithoutResult(status -> jobRepository.deleteById(job.getId()));
        } catch (IOException exception) {
            logger.warn("Failed to expire artifacts for job {}; cleanup will retry", job.getId(), exception);
        }
    }

    private void cleanupOrphanedObjects() {
        Instant cutoff = Instant.now().minus(properties.getOrphanGrace());
        try {
            for (StoredObjectInfo object : storageService.list("jobs")) {
                if (object.lastModified().isAfter(cutoff)) {
                    continue;
                }
                UUID jobId = extractJobId(object.key());
                if (jobId != null && !jobRepository.existsById(jobId)) {
                    storageService.delete(object.key());
                }
            }
        } catch (IOException exception) {
            logger.warn("Failed to scan orphaned job artifacts; cleanup will retry", exception);
        }
    }

    private UUID extractJobId(String key) {
        String[] parts = key.split("/", 3);
        if (parts.length < 3 || !parts[0].equals("jobs")) {
            return null;
        }
        try {
            return UUID.fromString(parts[1]);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
