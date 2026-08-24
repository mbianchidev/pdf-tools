package com.pdftools.jobs;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.pdftools.config.JobProperties;
import com.pdftools.jobs.api.JobResponse;
import com.pdftools.jobs.persistence.JobEntity;
import com.pdftools.jobs.persistence.JobInputEntity;
import com.pdftools.jobs.persistence.JobInputRepository;
import com.pdftools.jobs.persistence.JobOutputEntity;
import com.pdftools.jobs.persistence.JobOutputRepository;
import com.pdftools.jobs.persistence.JobRepository;
import com.pdftools.operations.OperationCancelledException;
import com.pdftools.operations.CheckpointInputStream;
import com.pdftools.operations.OperationContext;
import com.pdftools.operations.OperationException;
import com.pdftools.operations.OperationInput;
import com.pdftools.operations.OperationOutput;
import com.pdftools.operations.OperationRegistry;
import com.pdftools.operations.PdfOperation;
import com.pdftools.storage.StorageService;
import com.pdftools.storage.StoredObject;
import com.pdftools.storage.StoredResource;
import com.pdftools.util.FilenameSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class JobExecutionService {

    private static final Logger logger = LoggerFactory.getLogger(JobExecutionService.class);
    private static final long CANCELLATION_REFRESH_NANOS =
        TimeUnit.MILLISECONDS.toNanos(250);

    private final JobRepository jobRepository;
    private final JobInputRepository inputRepository;
    private final JobOutputRepository outputRepository;
    private final JobMapper jobMapper;
    private final JobEventService eventService;
    private final OperationRegistry operationRegistry;
    private final StorageService storageService;
    private final JobProperties properties;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final OptionsProtector optionsProtector;

    public JobExecutionService(
            JobRepository jobRepository,
            JobInputRepository inputRepository,
            JobOutputRepository outputRepository,
            JobMapper jobMapper,
            JobEventService eventService,
            OperationRegistry operationRegistry,
            StorageService storageService,
            JobProperties properties,
            ObjectMapper objectMapper,
            TransactionTemplate transactionTemplate,
            OptionsProtector optionsProtector) {
        this.jobRepository = jobRepository;
        this.inputRepository = inputRepository;
        this.outputRepository = outputRepository;
        this.jobMapper = jobMapper;
        this.eventService = eventService;
        this.operationRegistry = operationRegistry;
        this.storageService = storageService;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
        this.optionsProtector = optionsProtector;
    }

    public void execute(UUID jobId, String workerId) {
        Path workspace = null;
        try {
            if (!markRunning(jobId, workerId)) {
                publishCurrent(jobId);
                return;
            }
            publishCurrent(jobId);

            Files.createDirectories(properties.getWorkRoot());
            workspace = Files.createTempDirectory(properties.getWorkRoot(), jobId + "-");
            CancellationProbe cancellation = new CancellationProbe(jobId, workerId);
            List<OperationInput> inputs = materializeInputs(
                jobId,
                workspace,
                cancellation
            );
            JobEntity job = requireJob(jobId);
            JsonNode options = objectMapper.readTree(
                optionsProtector.unprotect(job.getOptionsJson())
            );
            PdfOperation operation = operationRegistry.find(job.getOperation())
                .orElseThrow(() -> new OperationException(
                    "OPERATION_NOT_REGISTERED",
                    "The job operation is not registered"
                ));

            OperationContext context = new OperationContext(
                jobId,
                options,
                inputs,
                workspace,
                progress -> updateProgress(jobId, workerId, progress),
                cancellation::isRequested
            );
            context.checkCancelled();
            List<OperationOutput> outputs = operation.execute(context);
            context.checkCancelled();
            if (outputs == null || outputs.isEmpty()) {
                throw new OperationException(
                    "NO_OUTPUTS",
                    "The PDF operation completed without producing an output"
                );
            }
            persistOutputs(
                jobId,
                workerId,
                workspace,
                outputs,
                cancellation
            );
        } catch (OperationCancelledException exception) {
            cancel(jobId, workerId);
        } catch (OperationException exception) {
            fail(jobId, workerId, exception.getCode(), exception.getMessage());
        } catch (Exception exception) {
            logger.error("PDF job {} failed unexpectedly", jobId, exception);
            fail(
                jobId,
                workerId,
                "INTERNAL_PROCESSING_ERROR",
                "The PDF operation failed unexpectedly"
            );
        } finally {
            cleanupWorkspace(workspace);
            publishCurrent(jobId);
        }
    }

    private boolean markRunning(UUID jobId, String workerId) {
        Boolean started = transactionTemplate.execute(status -> {
            JobEntity job = requireJobForUpdate(jobId);
            if (job.getStatus() != JobStatus.PENDING) {
                return false;
            }
            Instant now = Instant.now();
            if (job.isCancelRequested()) {
                job.cancel(now, now.plus(properties.getRetention()));
                jobRepository.save(job);
                return false;
            }
            job.start(now, workerId, now.plus(properties.getLeaseDuration()));
            jobRepository.save(job);
            return true;
        });
        return Boolean.TRUE.equals(started);
    }

    private List<OperationInput> materializeInputs(
            UUID jobId,
            Path workspace,
            CancellationProbe cancellation) throws IOException {
        List<OperationInput> inputs = new ArrayList<>();
        for (JobInputEntity input : inputRepository.findAllByJobIdOrderByPosition(jobId)) {
            cancellation.check();
            Path target = workspace.resolve(String.format(
                Locale.ROOT,
                "input-%04d.bin",
                input.getPosition()
            ));
            try (StoredResource resource = storageService.get(input.getStorageKey())) {
                try (CheckpointInputStream checkpointInput = new CheckpointInputStream(
                        resource.inputStream(),
                        cancellation::check)) {
                    Files.copy(checkpointInput, target);
                }
            }
            inputs.add(new OperationInput(
                input.getPosition(),
                target,
                input.getOriginalFilename(),
                input.getMediaType(),
                input.getSizeBytes(),
                input.getSha256()
            ));
        }
        return List.copyOf(inputs);
    }

    private void persistOutputs(
            UUID jobId,
            String workerId,
            Path workspace,
            List<OperationOutput> outputs,
            CancellationProbe cancellation) throws IOException {
        List<StoredOutput> storedOutputs = new ArrayList<>();
        List<String> storedKeys = new ArrayList<>();

        try {
            for (int index = 0; index < outputs.size(); index++) {
                OperationOutput output = outputs.get(index);
                Path outputPath = validateOutputPath(workspace, output.path());
                String filename = FilenameSanitizer.sanitize(
                    output.filename(),
                    "output-" + (index + 1)
                );
                String mediaType = output.mediaType() == null || output.mediaType().isBlank()
                    ? "application/octet-stream"
                    : output.mediaType();
                String storageKey = String.format(
                    Locale.ROOT,
                    "jobs/%s/outputs/%04d-%s-%s",
                    jobId,
                    index + 1,
                    UUID.randomUUID(),
                    filename
                );

                storedKeys.add(storageKey);
                try (InputStream fileInput = Files.newInputStream(outputPath);
                     CheckpointInputStream input = new CheckpointInputStream(
                         fileInput,
                         cancellation::check
                     )) {
                    StoredObject stored = storageService.put(
                        storageKey,
                        input,
                        Files.size(outputPath),
                        mediaType
                    );
                    storedOutputs.add(new StoredOutput(
                        index + 1,
                        filename,
                        stored
                    ));
                }
            }

            transactionTemplate.executeWithoutResult(status -> {
                JobEntity job = requireJobForUpdate(jobId);
                if (job.isCancelRequested()
                        || job.getStatus() != JobStatus.RUNNING
                        || !Objects.equals(job.getWorkerId(), workerId)) {
                    throw new OperationCancelledException();
                }
                Instant completedAt = Instant.now();
                Instant expiration = completedAt.plus(properties.getRetention());
                List<JobOutputEntity> entities = storedOutputs.stream()
                    .map(output -> new JobOutputEntity(
                        jobId,
                        output.position(),
                        output.filename(),
                        output.storedObject(),
                        completedAt,
                        expiration
                    ))
                    .toList();
                outputRepository.saveAll(entities);
                job.complete(completedAt, expiration);
                jobRepository.save(job);
            });
        } catch (IOException | RuntimeException exception) {
            for (String key : storedKeys) {
                try {
                    storageService.delete(key);
                } catch (IOException cleanupException) {
                    logger.warn("Failed to clean partial output {}", key, cleanupException);
                }
            }
            throw exception;
        }
    }

    private Path validateOutputPath(Path workspace, Path outputPath) {
        Path normalizedWorkspace = workspace.toAbsolutePath().normalize();
        Path normalizedOutput = outputPath.toAbsolutePath().normalize();
        if (!normalizedOutput.startsWith(normalizedWorkspace) || !Files.isRegularFile(normalizedOutput)) {
            throw new OperationException(
                "INVALID_OUTPUT_PATH",
                "The operation produced an invalid output path"
            );
        }
        return normalizedOutput;
    }

    private void updateProgress(UUID jobId, String workerId, int progress) {
        transactionTemplate.executeWithoutResult(status -> {
            JobEntity job = requireJobForUpdate(jobId);
            if (job.isCancelRequested()
                    || job.getStatus() != JobStatus.RUNNING
                    || !Objects.equals(job.getWorkerId(), workerId)) {
                throw new OperationCancelledException();
            }
            job.updateProgress(progress, "Processing", Instant.now());
            jobRepository.save(job);
        });
        publishCurrent(jobId);
    }

    private boolean isCancellationRequested(UUID jobId, String workerId) {
        return jobRepository.findById(jobId)
            .map(job -> job.isCancelRequested()
                || job.getStatus() != JobStatus.RUNNING
                || !Objects.equals(job.getWorkerId(), workerId))
            .orElse(true);
    }

    private void cancel(UUID jobId, String workerId) {
        transactionTemplate.executeWithoutResult(status -> {
            jobRepository.findByIdForUpdate(jobId).ifPresent(job -> {
                if (!job.getStatus().isTerminal()
                        && (job.getStatus() != JobStatus.RUNNING
                            || Objects.equals(job.getWorkerId(), workerId))) {
                    Instant now = Instant.now();
                    job.cancel(now, now.plus(properties.getRetention()));
                    jobRepository.save(job);
                }

            });
        });
    }

    private final class CancellationProbe {
        private final UUID jobId;
        private final String workerId;
        private long nextRefreshNanos;
        private boolean cancelled;

        private CancellationProbe(UUID jobId, String workerId) {
            this.jobId = jobId;
            this.workerId = workerId;
        }

        private boolean isRequested() {
            if (cancelled) {
                return true;
            }
            long now = System.nanoTime();
            if (now < nextRefreshNanos) {
                return false;
            }
            cancelled = isCancellationRequested(jobId, workerId);
            nextRefreshNanos = now + CANCELLATION_REFRESH_NANOS;
            return cancelled;
        }

        private void check() {
            if (isRequested()) {
                throw new OperationCancelledException();
            }
        }
    }

    private void fail(UUID jobId, String workerId, String code, String message) {
        transactionTemplate.executeWithoutResult(status -> {
            jobRepository.findByIdForUpdate(jobId).ifPresent(job -> {
                if (job.getStatus() == JobStatus.RUNNING
                        && !Objects.equals(job.getWorkerId(), workerId)) {
                    return;
                }
                Instant now = Instant.now();
                if (job.isCancelRequested()) {
                    job.cancel(now, now.plus(properties.getRetention()));
                } else {
                    job.fail(code, message, now, now.plus(properties.getRetention()));
                }
                jobRepository.save(job);
            });
        });
    }

    public void renewLease(UUID jobId, String workerId) {
        transactionTemplate.executeWithoutResult(status ->
            jobRepository.findByIdForUpdate(jobId).ifPresent(job -> {
                if (job.getStatus() == JobStatus.RUNNING
                        && Objects.equals(job.getWorkerId(), workerId)) {
                    job.renewLease(
                        workerId,
                        Instant.now().plus(properties.getLeaseDuration())
                    );
                    jobRepository.save(job);
                }
            })
        );
    }

    private JobEntity requireJob(UUID jobId) {
        return jobRepository.findById(jobId)
            .orElseThrow(() -> new IllegalStateException("PDF job disappeared during execution"));
    }

    private JobEntity requireJobForUpdate(UUID jobId) {
        return jobRepository.findByIdForUpdate(jobId)
            .orElseThrow(() -> new IllegalStateException("PDF job disappeared during execution"));
    }

    private void publishCurrent(UUID jobId) {
        jobRepository.findById(jobId)
            .map(jobMapper::toResponse)
            .ifPresent(eventService::publish);
    }

    private void cleanupWorkspace(Path workspace) {
        if (workspace == null || !Files.exists(workspace)) {
            return;
        }
        try (var paths = Files.walk(workspace)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException exception) {
                    logger.warn("Failed to remove job workspace path {}", path, exception);
                }
            });
        } catch (IOException exception) {
            logger.warn("Failed to inspect job workspace {}", workspace, exception);
        }
    }

    private record StoredOutput(
        int position,
        String filename,
        StoredObject storedObject
    ) {
    }
}
