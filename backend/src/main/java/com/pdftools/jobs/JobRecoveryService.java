package com.pdftools.jobs;

import com.pdftools.config.JobProperties;
import com.pdftools.jobs.persistence.JobRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class JobRecoveryService {

    private static final Logger logger = LoggerFactory.getLogger(JobRecoveryService.class);

    private final JobRepository jobRepository;
    private final JobMapper jobMapper;
    private final JobEventService eventService;
    private final JobDispatcher dispatcher;
    private final JobProperties properties;
    private final TransactionTemplate transactionTemplate;

    public JobRecoveryService(
            JobRepository jobRepository,
            JobMapper jobMapper,
            JobEventService eventService,
            JobDispatcher dispatcher,
            JobProperties properties,
            TransactionTemplate transactionTemplate) {
        this.jobRepository = jobRepository;
        this.jobMapper = jobMapper;
        this.eventService = eventService;
        this.dispatcher = dispatcher;
        this.properties = properties;
        this.transactionTemplate = transactionTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverAfterRestart() {
        recoverStaleWork();
        dispatcher.dispatchPending();
    }

    @Scheduled(fixedDelayString = "${pdf.jobs.cleanup-interval:15m}")
    public void recoverStaleWork() {
        Instant now = Instant.now();
        Instant uploadCutoff = now.minus(properties.getOrphanGrace());
        List<UUID> uploadCandidates = jobRepository
            .findAllByStatusAndUpdatedAtBefore(JobStatus.UPLOADING, uploadCutoff)
            .stream()
            .map(job -> job.getId())
            .toList();
        List<UUID> workerCandidates = jobRepository
            .findAllByStatusAndLeaseExpiresAtBefore(JobStatus.RUNNING, now)
            .stream()
            .map(job -> job.getId())
            .toList();

        for (UUID jobId : uploadCandidates) {
            recover(jobId, JobStatus.UPLOADING, uploadCutoff);
        }
        for (UUID jobId : workerCandidates) {
            recover(jobId, JobStatus.RUNNING, now);
        }
        cleanupStaleWorkspaces();
    }

    private void recover(UUID jobId, JobStatus expectedStatus, Instant cutoff) {
        UUID recoveredId = transactionTemplate.execute(status ->
            jobRepository.findByIdForUpdate(jobId)
                .filter(job -> job.getStatus() == expectedStatus)
                .filter(job -> expectedStatus == JobStatus.UPLOADING
                    ? !job.getUpdatedAt().isAfter(cutoff)
                    : job.getLeaseExpiresAt() != null
                        && !job.getLeaseExpiresAt().isAfter(cutoff))
                .map(job -> {
                    Instant failedAt = Instant.now();
                    if (job.isCancelRequested()) {
                        job.cancel(
                            failedAt,
                            failedAt.plus(properties.getRetention())
                        );
                        return jobRepository.save(job).getId();
                    }
                    String code = expectedStatus == JobStatus.UPLOADING
                        ? "UPLOAD_INTERRUPTED"
                        : "WORKER_LEASE_EXPIRED";
                    String message = expectedStatus == JobStatus.UPLOADING
                        ? "The upload did not finish"
                        : "The worker lease expired before the job completed";
                    job.fail(
                        code,
                        message,
                        failedAt,
                        failedAt.plus(properties.getRetention())
                    );
                    return jobRepository.save(job).getId();
                })
                .orElse(null)
        );
        if (recoveredId != null) {
            jobRepository.findById(recoveredId)
                .map(jobMapper::toResponse)
                .ifPresent(eventService::publish);
        }
    }

    private void cleanupStaleWorkspaces() {
        Path root = properties.getWorkRoot().toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            return;
        }

        try (var paths = Files.list(root)) {
            paths.filter(Files::isDirectory).forEach(path -> {
                UUID jobId = workspaceJobId(path);
                if (jobId == null || hasRunningJob(jobId)) {
                    return;
                }
                deleteWorkspace(path);
            });
        } catch (IOException exception) {
            logger.warn("Failed to scan stale PDF job workspaces", exception);
        }
    }

    private UUID workspaceJobId(Path workspace) {
        String name = workspace.getFileName().toString();
        if (name.length() < 37 || name.charAt(36) != '-') {
            return null;
        }
        try {
            return UUID.fromString(name.substring(0, 36));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private boolean hasRunningJob(UUID jobId) {
        return jobRepository.findById(jobId)
            .map(job -> job.getStatus() == JobStatus.RUNNING)
            .orElse(false);
    }

    private void deleteWorkspace(Path workspace) {
        try (var paths = Files.walk(workspace)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException exception) {
                    logger.warn("Failed to delete stale workspace path {}", path, exception);
                }
            });
        } catch (IOException exception) {
            logger.warn("Failed to inspect stale workspace {}", workspace, exception);
        }
    }
}
