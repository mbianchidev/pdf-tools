package com.pdftools.jobs;

import com.pdftools.jobs.persistence.JobEntity;
import com.pdftools.jobs.persistence.JobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class JobDispatcher {

    private static final Logger logger = LoggerFactory.getLogger(JobDispatcher.class);

    private final JobRepository jobRepository;
    private final JobExecutionService executionService;
    private final TaskExecutor taskExecutor;
    private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();
    private final String workerId = UUID.randomUUID().toString();

    public JobDispatcher(
            JobRepository jobRepository,
            JobExecutionService executionService,
            @Qualifier("pdfJobExecutor") TaskExecutor taskExecutor) {
        this.jobRepository = jobRepository;
        this.executionService = executionService;
        this.taskExecutor = taskExecutor;
    }

    @Scheduled(
        fixedDelayString = "${pdf.jobs.lease-renew-interval:30s}",
        scheduler = "leaseTaskScheduler"
    )
    public void renewLeases() {
        inFlight.forEach(jobId -> executionService.renewLease(jobId, workerId));
    }

    @Scheduled(fixedDelayString = "${pdf.jobs.dispatch-interval:1s}")
    public void dispatchPending() {
        jobRepository.findTop20ByStatusOrderByCreatedAtAsc(JobStatus.PENDING)
            .stream()
            .map(JobEntity::getId)
            .forEach(this::dispatch);
    }

    public void dispatch(UUID jobId) {
        if (!inFlight.add(jobId)) {
            return;
        }
        try {
            taskExecutor.execute(() -> {
                try {
                    executionService.execute(jobId, workerId);
                } finally {
                    inFlight.remove(jobId);
                }
            });
        } catch (TaskRejectedException exception) {
            inFlight.remove(jobId);
            logger.warn("PDF job queue is full; job {} remains pending for retry", jobId);
        }
    }
}
