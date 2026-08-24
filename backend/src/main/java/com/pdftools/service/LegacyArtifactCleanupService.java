package com.pdftools.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

@Service
public class LegacyArtifactCleanupService {

    private static final Logger logger =
        LoggerFactory.getLogger(LegacyArtifactCleanupService.class);

    private final Path uploadDirectory;
    private final Duration retention;

    public LegacyArtifactCleanupService(
            @Value("${pdf.upload.dir}") String uploadDirectory,
            @Value("${pdf.jobs.retention:2h}") Duration retention) {
        this.uploadDirectory = Path.of(uploadDirectory);
        this.retention = retention;
    }

    @Scheduled(fixedDelayString = "${pdf.jobs.cleanup-interval:15m}")
    public void cleanupExpiredArtifacts() {
        if (!Files.isDirectory(uploadDirectory)) {
            return;
        }
        Instant cutoff = Instant.now().minus(retention);
        try (var paths = Files.list(uploadDirectory)) {
            paths.filter(Files::isRegularFile).forEach(path -> {
                try {
                    if (Files.getLastModifiedTime(path).toInstant().isBefore(cutoff)) {
                        Files.deleteIfExists(path);
                    }
                } catch (IOException exception) {
                    logger.warn(
                        "Failed to expire legacy artifact {}",
                        path,
                        exception
                    );
                }
            });
        } catch (IOException exception) {
            logger.warn(
                "Failed to inspect legacy artifact directory {}",
                uploadDirectory,
                exception
            );
        }
    }
}
