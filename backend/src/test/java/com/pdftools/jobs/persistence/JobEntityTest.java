package com.pdftools.jobs.persistence;

import com.pdftools.jobs.JobStatus;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JobEntityTest {

    @Test
    void followsTheExpectedLifecycle() {
        Instant created = Instant.parse("2026-08-23T08:00:00Z");
        JobEntity job = JobEntity.pending(
            "merge",
            "{}",
            created,
            created.plus(Duration.ofHours(2))
        );

        job.start(created.plusSeconds(1));
        job.updateProgress(50, "Halfway", created.plusSeconds(2));
        job.complete(created.plusSeconds(3), created.plus(Duration.ofHours(2)));

        assertEquals(JobStatus.COMPLETED, job.getStatus());
        assertEquals(100, job.getProgress());
        assertEquals("Completed", job.getMessage());
    }

    @Test
    void cancellationIsTerminal() {
        Instant created = Instant.parse("2026-08-23T08:00:00Z");
        JobEntity job = JobEntity.pending(
            "merge",
            "{}",
            created,
            created.plus(Duration.ofHours(2))
        );

        job.requestCancellation(created.plusSeconds(1));
        job.cancel(created.plusSeconds(2), created.plus(Duration.ofHours(2)));

        assertTrue(job.getStatus().isTerminal());
        assertThrows(
            IllegalStateException.class,
            () -> job.requestCancellation(created.plusSeconds(3))
        );
    }

    @Test
    void tracksAndClearsWorkerLeases() {
        Instant created = Instant.parse("2026-08-23T08:00:00Z");
        JobEntity job = JobEntity.pending(
            "merge",
            "{}",
            created,
            created.plus(Duration.ofHours(2))
        );
        Instant firstLease = created.plus(Duration.ofMinutes(2));
        Instant renewedLease = created.plus(Duration.ofMinutes(3));

        job.start(created.plusSeconds(1), "worker-1", firstLease);
        job.renewLease("worker-1", renewedLease);

        assertEquals("worker-1", job.getWorkerId());
        assertEquals(renewedLease, job.getLeaseExpiresAt());

        job.complete(created.plusSeconds(2), created.plus(Duration.ofHours(2)));
        assertNull(job.getWorkerId());
        assertNull(job.getLeaseExpiresAt());
    }
}
