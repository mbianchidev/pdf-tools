package com.pdftools.jobs.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JobOutputRepository extends JpaRepository<JobOutputEntity, UUID> {

    List<JobOutputEntity> findAllByJobIdOrderByPosition(UUID jobId);

    Optional<JobOutputEntity> findByIdAndJobId(UUID id, UUID jobId);
}
