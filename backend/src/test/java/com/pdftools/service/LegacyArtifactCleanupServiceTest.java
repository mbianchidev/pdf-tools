package com.pdftools.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyArtifactCleanupServiceTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void removesOnlyExpiredRegularArtifacts() throws Exception {
        Path expired = Files.writeString(
            temporaryDirectory.resolve("expired.zip"),
            "expired"
        );
        Files.setLastModifiedTime(
            expired,
            FileTime.from(Instant.now().minus(Duration.ofHours(3)))
        );
        Path current = Files.writeString(
            temporaryDirectory.resolve("current.zip"),
            "current"
        );
        Path directory = Files.createDirectory(
            temporaryDirectory.resolve("workspace")
        );

        new LegacyArtifactCleanupService(
            temporaryDirectory.toString(),
            Duration.ofHours(2)
        ).cleanupExpiredArtifacts();

        assertFalse(Files.exists(expired));
        assertTrue(Files.exists(current));
        assertTrue(Files.isDirectory(directory));
    }
}
