package com.pdftools.storage;

import com.pdftools.config.StorageProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LocalStorageServiceTest {

    @TempDir
    Path tempDirectory;

    @Test
    void streamsObjectsAndReturnsStableMetadata() throws Exception {
        StorageProperties properties = new StorageProperties();
        properties.setLocalRoot(tempDirectory);
        LocalStorageService storage = new LocalStorageService(properties);
        byte[] content = "streamed content".getBytes(StandardCharsets.UTF_8);

        StoredObject stored = storage.put(
            "jobs/test/input.txt",
            new ByteArrayInputStream(content),
            content.length,
            "text/plain"
        );

        assertEquals(content.length, stored.sizeBytes());
        assertEquals(64, stored.sha256().length());
        assertEquals(
            java.util.List.of(stored.key()),
            storage.list("jobs").stream().map(StoredObjectInfo::key).toList()
        );
        try (StoredResource resource = storage.get(stored.key())) {
            assertArrayEquals(content, resource.inputStream().readAllBytes());
        }

        storage.delete(stored.key());
        assertFalse(tempDirectory.resolve(stored.key()).toFile().exists());
    }

    @Test
    void rejectsKeysOutsideTheConfiguredRoot() {
        StorageProperties properties = new StorageProperties();
        properties.setLocalRoot(tempDirectory);
        LocalStorageService storage = new LocalStorageService(properties);

        assertThrows(
            IllegalArgumentException.class,
            () -> storage.put(
                "../escape.txt",
                new ByteArrayInputStream(new byte[0]),
                0,
                "text/plain"
            )
        );
    }
}
