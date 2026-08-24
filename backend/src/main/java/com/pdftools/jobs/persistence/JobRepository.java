package com.pdftools.jobs.persistence;

import com.pdftools.jobs.JobStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JobRepository extends JpaRepository<JobEntity, UUID> {

    List<JobEntity> findAllByExpiresAtBeforeAndStatusIn(
        Instant expiration,
        Collection<JobStatus> statuses
    );

    List<JobEntity> findTop20ByStatusAndOperationInOrderByCreatedAtAsc(
        JobStatus status,
        Collection<String> operations
    );

    List<JobEntity> findAllByStatusAndUpdatedAtBefore(JobStatus status, Instant updatedBefore);

    List<JobEntity> findAllByStatusAndLeaseExpiresAtBefore(
        JobStatus status,
        Instant leaseExpiresBefore
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select job from JobEntity job where job.id = :jobId")
    Optional<JobEntity> findByIdForUpdate(@Param("jobId") UUID jobId);
}
