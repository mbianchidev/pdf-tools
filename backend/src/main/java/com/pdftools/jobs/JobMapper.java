package com.pdftools.jobs;

import com.pdftools.jobs.api.JobOutputResponse;
import com.pdftools.jobs.api.JobResponse;
import com.pdftools.jobs.persistence.JobEntity;
import com.pdftools.jobs.persistence.JobOutputEntity;
import com.pdftools.jobs.persistence.JobOutputRepository;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class JobMapper {

    private final JobOutputRepository outputRepository;

    public JobMapper(JobOutputRepository outputRepository) {
        this.outputRepository = outputRepository;
    }

    public JobResponse toResponse(JobEntity job) {
        List<JobOutputResponse> outputs = job.getStatus() == JobStatus.COMPLETED
            ? outputRepository
                .findAllByJobIdOrderByPosition(job.getId())
                .stream()
                .map(this::toOutputResponse)
                .toList()
            : List.of();

        return new JobResponse(
            job.getId(),
            job.getOperation(),
            job.getStatus(),
            job.getVersion(),
            job.getProgress(),
            job.getMessage(),
            job.getErrorCode(),
            job.getErrorMessage(),
            job.isCancelRequested(),
            job.getCreatedAt(),
            job.getUpdatedAt(),
            job.getExpiresAt(),
            outputs
        );
    }

    private JobOutputResponse toOutputResponse(JobOutputEntity output) {
        return new JobOutputResponse(
            output.getId(),
            output.getPosition(),
            output.getFilename(),
            output.getMediaType(),
            output.getSizeBytes(),
            output.getSha256(),
            output.getExpiresAt(),
            "/api/v1/jobs/" + output.getJobId() + "/outputs/" + output.getId()
        );
    }
}
