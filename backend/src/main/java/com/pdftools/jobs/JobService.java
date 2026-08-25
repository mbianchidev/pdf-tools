package com.pdftools.jobs;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.pdftools.api.ApiException;
import com.pdftools.config.JobProperties;
import com.pdftools.jobs.api.JobResponse;
import com.pdftools.jobs.persistence.JobEntity;
import com.pdftools.jobs.persistence.JobInputEntity;
import com.pdftools.jobs.persistence.JobInputRepository;
import com.pdftools.jobs.persistence.JobOutputEntity;
import com.pdftools.jobs.persistence.JobOutputRepository;
import com.pdftools.jobs.persistence.JobRepository;
import com.pdftools.operations.OperationException;
import com.pdftools.operations.OperationRegistry;
import com.pdftools.operations.OperationSubmission;
import com.pdftools.operations.PdfOperation;
import com.pdftools.storage.StorageService;
import com.pdftools.storage.StoredObject;
import com.pdftools.util.FilenameSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class JobService {

    private static final Logger logger = LoggerFactory.getLogger(JobService.class);
    private static final Pattern OPERATION_KEY = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");
    private static final int MAX_OPTIONS_LENGTH = 65_536;

    private final JobRepository jobRepository;
    private final JobInputRepository inputRepository;
    private final JobOutputRepository outputRepository;
    private final JobMapper jobMapper;
    private final OperationRegistry operationRegistry;
    private final StorageService storageService;
    private final JobProperties properties;
    private final ObjectMapper objectMapper;
    private final JobDispatcher dispatcher;
    private final JobEventService eventService;
    private final TransactionTemplate transactionTemplate;
    private final OptionsProtector optionsProtector;

    public JobService(
            JobRepository jobRepository,
            JobInputRepository inputRepository,
            JobOutputRepository outputRepository,
            JobMapper jobMapper,
            OperationRegistry operationRegistry,
            StorageService storageService,
            JobProperties properties,
            ObjectMapper objectMapper,
            JobDispatcher dispatcher,
            JobEventService eventService,
            TransactionTemplate transactionTemplate,
            OptionsProtector optionsProtector) {
        this.jobRepository = jobRepository;
        this.inputRepository = inputRepository;
        this.outputRepository = outputRepository;
        this.jobMapper = jobMapper;
        this.operationRegistry = operationRegistry;
        this.storageService = storageService;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.dispatcher = dispatcher;
        this.eventService = eventService;
        this.transactionTemplate = transactionTemplate;
        this.optionsProtector = optionsProtector;
    }

    public JobResponse create(String operation, String options, List<MultipartFile> files) {
        String normalizedOperation = validateOperation(operation);
        PdfOperation pdfOperation = operationRegistry.find(normalizedOperation)
            .orElseThrow(() -> operationUnavailable(normalizedOperation));
        if (!properties.getEnabledOperations().contains(normalizedOperation)) {
            throw operationUnavailable(normalizedOperation);
        }

        JsonNode optionNode = parseOptions(options);
        List<OperationSubmission.UploadDescriptor> descriptors = validateFiles(files);
        try {
            pdfOperation.validateSubmission(new OperationSubmission(optionNode, descriptors));
        } catch (OperationException exception) {
            throw new ApiException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                exception.getCode(),
                exception.getMessage(),
                exception.getDetails()
            );
        }

        Instant now = Instant.now();
        String storedOptions = pdfOperation.hasSensitiveOptions()
            ? optionsProtector.protect(optionNode.toString())
            : optionNode.toString();
        JobEntity job = transactionTemplate.execute(status -> jobRepository.saveAndFlush(
            JobEntity.uploading(
                normalizedOperation,
                storedOptions,
                now,
                now.plus(properties.getRetention())
            )
        ));
        if (job == null) {
            throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "JOB_CREATION_FAILED",
                "The PDF job could not be created"
            );
        }

        List<String> storedKeys = new ArrayList<>();
        List<JobInputEntity> inputEntities = new ArrayList<>();
        try {
            for (int index = 0; index < files.size(); index++) {
                MultipartFile file = files.get(index);
                String filename = descriptors.get(index).filename();
                String storageKey = String.format(
                    Locale.ROOT,
                    "jobs/%s/inputs/%04d-%s",
                    job.getId(),
                    index + 1,
                    filename
                );
                String mediaType = descriptors.get(index).mediaType();
                storedKeys.add(storageKey);
                try (InputStream input = file.getInputStream()) {
                    StoredObject storedObject = storageService.put(
                        storageKey,
                        input,
                        file.getSize(),
                        mediaType
                    );
                    inputEntities.add(new JobInputEntity(
                        job.getId(),
                        index + 1,
                        filename,
                        storedObject,
                        now
                    ));
                }
            }
            transactionTemplate.executeWithoutResult(status -> {
                JobEntity lockedJob = requireJobForUpdate(job.getId());
                inputRepository.saveAll(inputEntities);
                lockedJob.queue(Instant.now());
                jobRepository.save(lockedJob);
            });
            dispatcher.dispatch(job.getId());
        } catch (IOException | RuntimeException exception) {
            deleteStoredObjects(storedKeys);
            deleteCreatedJob(job.getId());
            throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INPUT_STORAGE_FAILED",
                "The uploaded files could not be stored"
            );
        }

        return get(job.getId());
    }

    @Transactional(readOnly = true)
    public JobResponse get(UUID jobId) {
        return jobMapper.toResponse(requireJob(jobId));
    }

    @Transactional
    public JobResponse cancel(UUID jobId) {
        JobEntity job = requireJobForUpdate(jobId);
        if (job.getStatus().isTerminal()) {
            throw new ApiException(
                HttpStatus.CONFLICT,
                "JOB_ALREADY_TERMINAL",
                "The job is already " + job.getStatus().name().toLowerCase(Locale.ROOT),
                Map.of("status", job.getStatus())
            );
        }

        Instant now = Instant.now();
        job.requestCancellation(now);
        if (job.getStatus() == JobStatus.PENDING) {
            job.cancel(now, now.plus(properties.getRetention()));
        }
        JobEntity saved = jobRepository.saveAndFlush(job);
        JobResponse response = jobMapper.toResponse(saved);
        publishAfterCommit(jobId);
        return response;
    }

    @Transactional(readOnly = true)
    public JobOutputEntity getOutput(UUID jobId, UUID outputId) {
        JobEntity job = requireJob(jobId);
        if (job.getStatus() != JobStatus.COMPLETED) {
            throw new ApiException(
                HttpStatus.CONFLICT,
                "JOB_OUTPUTS_NOT_READY",
                "Job outputs are available only after successful completion"
            );
        }
        return outputRepository.findByIdAndJobId(outputId, jobId)
            .orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND,
                "OUTPUT_NOT_FOUND",
                "The requested job output does not exist"
            ));
    }

    private JobEntity requireJob(UUID jobId) {
        JobEntity job = jobRepository.findById(jobId)
            .orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND,
                "JOB_NOT_FOUND",
                "The requested PDF job does not exist"
            ));
        return requireUnexpired(job);
    }

    private JobEntity requireJobForUpdate(UUID jobId) {
        JobEntity job = jobRepository.findByIdForUpdate(jobId)
            .orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND,
                "JOB_NOT_FOUND",
                "The requested PDF job does not exist"
            ));
        return requireUnexpired(job);
    }

    private JobEntity requireUnexpired(JobEntity job) {
        if (isExpired(job)) {
            throw new ApiException(
                HttpStatus.GONE,
                "JOB_EXPIRED",
                "The PDF job and its artifacts have expired"
            );
        }
        return job;
    }

    private boolean isExpired(JobEntity job) {
        return job.getStatus().isTerminal()
            && !job.getExpiresAt().isAfter(Instant.now());
    }

    private String validateOperation(String operation) {
        String normalized = operation == null ? "" : operation.trim().toLowerCase(Locale.ROOT);
        if (normalized.getBytes(StandardCharsets.UTF_8).length > 64
                || !OPERATION_KEY.matcher(normalized).matches()) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "INVALID_OPERATION",
                "Operation keys must use lowercase kebab-case"
            );
        }
        return normalized;
    }

    private JsonNode parseOptions(String options) {
        String json = options == null || options.isBlank() ? "{}" : options;
        if (json.getBytes(StandardCharsets.UTF_8).length > MAX_OPTIONS_LENGTH) {
            throw new ApiException(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "OPTIONS_TOO_LARGE",
                "Job options exceed the 64 KiB limit"
            );
        }
        try {
            JsonNode parsed = objectMapper.readTree(json);
            if (!parsed.isObject()) {
                throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_OPTIONS",
                    "Job options must be a JSON object"
                );
            }
            return parsed;
        } catch (JacksonException exception) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "INVALID_OPTIONS_JSON",
                "Job options must contain valid JSON"
            );
        }
    }

    private List<OperationSubmission.UploadDescriptor> validateFiles(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "FILES_REQUIRED",
                "At least one input file is required"
            );
        }
        if (files.size() > properties.getMaxFiles()) {
            throw new ApiException(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "TOO_MANY_FILES",
                "A job accepts at most " + properties.getMaxFiles() + " files"
            );
        }

        List<OperationSubmission.UploadDescriptor> descriptors = new ArrayList<>();
        for (int index = 0; index < files.size(); index++) {
            MultipartFile file = files.get(index);
            if (file == null || file.isEmpty()) {
                throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "EMPTY_FILE",
                    "Input file " + (index + 1) + " is empty",
                    Map.of("position", index + 1)
                );
            }
            if (file.getSize() > properties.getMaxFileSizeBytes()) {
                throw new ApiException(
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    "FILE_TOO_LARGE",
                    "Input file " + (index + 1) + " exceeds the per-file limit",
                    Map.of(
                        "position", index + 1,
                        "maxSizeBytes", properties.getMaxFileSizeBytes()
                    )
                );
            }

            String filename = FilenameSanitizer.sanitize(
                file.getOriginalFilename(),
                "input-" + (index + 1)
            );
            String mediaType = file.getContentType() == null || file.getContentType().isBlank()
                ? "application/octet-stream"
                : file.getContentType();
            descriptors.add(new OperationSubmission.UploadDescriptor(
                index + 1,
                filename,
                mediaType,
                file.getSize()
            ));
        }
        return List.copyOf(descriptors);
    }

    private ApiException operationUnavailable(String operation) {
        return new ApiException(
            HttpStatus.NOT_FOUND,
            "OPERATION_NOT_AVAILABLE",
            "The requested PDF operation is not enabled",
            Map.of("operation", operation)
        );
    }

    private void publishAfterCommit(UUID jobId) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                jobRepository.findById(jobId)
                    .map(jobMapper::toResponse)
                    .ifPresent(eventService::publish);
            }
        });
    }

    private void deleteCreatedJob(UUID jobId) {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                if (jobRepository.existsById(jobId)) {
                    jobRepository.deleteById(jobId);
                }
            });
        } catch (RuntimeException exception) {
            logger.warn("Failed to remove incomplete job metadata {}", jobId, exception);
        }
    }

    private void deleteStoredObjects(List<String> keys) {
        for (String key : keys) {
            try {
                storageService.delete(key);
            } catch (IOException exception) {
                logger.warn("Failed to remove rolled-back input object {}", key, exception);
            }
        }
    }
}
