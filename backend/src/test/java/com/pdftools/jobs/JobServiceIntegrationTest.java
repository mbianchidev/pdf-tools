package com.pdftools.jobs;

import com.pdftools.config.JobProperties;
import com.pdftools.api.ApiException;
import com.pdftools.jobs.api.JobResponse;
import com.pdftools.jobs.persistence.JobOutputEntity;
import com.pdftools.jobs.persistence.JobEntity;
import com.pdftools.jobs.persistence.JobRepository;
import com.pdftools.operations.OperationContext;
import com.pdftools.operations.OperationOutput;
import com.pdftools.operations.PdfOperation;
import com.pdftools.storage.StorageService;
import com.pdftools.storage.StoredResource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
    "pdf.jobs.enabled-operations=test-copy,test-blocking"
})
@Import(JobServiceIntegrationTest.TestOperations.class)
class JobServiceIntegrationTest {

    @Autowired
    private JobService jobService;

    @Autowired
    private StorageService storageService;

    @Autowired
    private BlockingOperation blockingOperation;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private JobRecoveryService recoveryService;

    @Autowired
    private JobProperties jobProperties;

    @Test
    void persistsRunsAndStreamsAJobOutput() throws Exception {
        byte[] content = "streamed PDF fixture".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile input = new MockMultipartFile(
            "files",
            "fixture.pdf",
            "application/pdf",
            content
        );

        JobResponse created = jobService.create("test-copy", "{}", List.of(input));
        JobResponse completed = awaitTerminal(created.id());

        assertEquals(JobStatus.COMPLETED, completed.status());
        assertEquals(100, completed.progress());
        assertEquals(1, completed.outputs().size());
        assertEquals(completed.expiresAt(), completed.outputs().getFirst().expiresAt());

        JobOutputEntity output = jobService.getOutput(
            completed.id(),
            completed.outputs().getFirst().id()
        );
        try (StoredResource resource = storageService.get(output.getStorageKey())) {
            assertArrayEquals(content, resource.inputStream().readAllBytes());
        }
        assertEquals(
            1,
            storageService.list("jobs/" + completed.id() + "/outputs").size()
        );
    }

    @Test
    void cancelsRunningWorkCooperatively() throws Exception {
        MockMultipartFile input = new MockMultipartFile(
            "files",
            "fixture.pdf",
            "application/pdf",
            new byte[]{1, 2, 3}
        );

        JobResponse created = jobService.create("test-blocking", "{}", List.of(input));
        assertTrue(blockingOperation.awaitStarted(Duration.ofSeconds(5)));

        JobResponse cancellation = jobService.cancel(created.id());
        assertEquals(cancellation.version(), jobService.get(created.id()).version());
        JobResponse cancelled = awaitTerminal(created.id());

        assertEquals(JobStatus.CANCELLED, cancelled.status());
        assertTrue(cancelled.outputs().isEmpty());
    }

    @Test
    void recoversExpiredLeasesAndStaleWorkspaces() throws Exception {
        Instant now = Instant.now();
        JobEntity expired = JobEntity.pending(
            "test-copy",
            "{}",
            now.minusSeconds(10),
            now.plusSeconds(120)
        );
        expired.start(now.minusSeconds(5), "expired-worker", now.minusSeconds(1));
        JobEntity active = JobEntity.pending(
            "test-copy",
            "{}",
            now.minusSeconds(10),
            now.plusSeconds(120)
        );
        active.start(now.minusSeconds(5), "active-worker", now.plusSeconds(60));
        JobEntity cancelled = JobEntity.pending(
            "test-copy",
            "{}",
            now.minusSeconds(10),
            now.plusSeconds(120)
        );
        cancelled.start(now.minusSeconds(5), "cancelled-worker", now.minusSeconds(1));
        cancelled.requestCancellation(now);
        jobRepository.saveAllAndFlush(List.of(expired, active, cancelled));
        Path orphanWorkspace = Files.createDirectories(
            jobProperties.getWorkRoot().resolve(UUID.randomUUID() + "-stale")
        );
        Files.writeString(orphanWorkspace.resolve("sensitive.tmp"), "temporary");
        Path activeWorkspace = Files.createDirectories(
            jobProperties.getWorkRoot().resolve(active.getId() + "-active")
        );
        Files.writeString(activeWorkspace.resolve("in-use.tmp"), "temporary");

        recoveryService.recoverStaleWork();

        assertEquals(JobStatus.FAILED, jobRepository.findById(expired.getId()).orElseThrow().getStatus());
        assertEquals(JobStatus.RUNNING, jobRepository.findById(active.getId()).orElseThrow().getStatus());
        assertEquals(
            JobStatus.CANCELLED,
            jobRepository.findById(cancelled.getId()).orElseThrow().getStatus()
        );
        assertFalse(Files.exists(orphanWorkspace));
        assertTrue(Files.exists(activeWorkspace));

        jobRepository.deleteAllById(List.of(expired.getId(), active.getId(), cancelled.getId()));
        recoveryService.recoverStaleWork();
        assertFalse(Files.exists(activeWorkspace));
    }

    @Test
    void rejectsExpiredTerminalJobsBeforeAsynchronousCleanup() {
        Instant now = Instant.now();
        JobEntity expired = JobEntity.pending(
            "test-copy",
            "{}",
            now.minus(Duration.ofHours(3)),
            now.minus(Duration.ofHours(1))
        );
        expired.start(now.minus(Duration.ofHours(2)));
        expired.complete(now.minus(Duration.ofHours(2)), now.minusSeconds(1));
        jobRepository.saveAndFlush(expired);

        ApiException exception = assertThrows(
            ApiException.class,
            () -> jobService.get(expired.getId())
        );

        assertEquals(HttpStatus.GONE, exception.getStatus());
        assertEquals("JOB_EXPIRED", exception.getCode());
        jobRepository.deleteById(expired.getId());
    }

    private JobResponse awaitTerminal(java.util.UUID jobId) throws InterruptedException {
        Instant deadline = Instant.now().plusSeconds(5);
        JobResponse current;
        do {
            current = jobService.get(jobId);
            if (current.status().isTerminal()) {
                return current;
            }
            Thread.sleep(20);
        } while (Instant.now().isBefore(deadline));

        throw new AssertionError("Job did not reach a terminal state: " + current.status());
    }

    @TestConfiguration
    static class TestOperations {

        @Bean
        PdfOperation copyOperation() {
            return new PdfOperation() {
                @Override
                public String key() {
                    return "test-copy";
                }

                @Override
                public List<OperationOutput> execute(OperationContext context) {
                    try {
                        context.reportProgress(50);
                        Path output = context.workspace().resolve("copied.pdf");
                        Files.copy(context.inputs().getFirst().path(), output);
                        return List.of(new OperationOutput(
                            output,
                            "copied.pdf",
                            "application/pdf"
                        ));
                    } catch (java.io.IOException exception) {
                        throw new IllegalStateException(exception);
                    }
                }
            };
        }

        @Bean
        BlockingOperation blockingOperation() {
            return new BlockingOperation();
        }
    }

    static class BlockingOperation implements PdfOperation {

        private final CountDownLatch started = new CountDownLatch(1);

        @Override
        public String key() {
            return "test-blocking";
        }

        @Override
        public List<OperationOutput> execute(OperationContext context) {
            started.countDown();
            while (true) {
                context.checkCancelled();
                try {
                    Thread.sleep(10);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    context.checkCancelled();
                }
            }
        }

        boolean awaitStarted(Duration timeout) throws InterruptedException {
            return started.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
        }
    }
}
