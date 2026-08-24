package com.pdftools.operations;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ZipArtifactServiceTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void deterministicallyDisambiguatesSanitizedEntryNames() throws Exception {
        Path first = temporaryDirectory.resolve("first.pdf");
        Path second = temporaryDirectory.resolve("second.pdf");
        Path zipPath = temporaryDirectory.resolve("outputs.zip");
        Files.writeString(first, "first");
        Files.writeString(second, "second");
        String sharedPrefix = "文".repeat(100);

        new ZipArtifactService().create(
            List.of(
                new OperationOutput(first, sharedPrefix + "A.pdf", "application/pdf"),
                new OperationOutput(second, sharedPrefix + "B.pdf", "application/pdf")
            ),
            zipPath,
            "outputs.zip"
        );

        List<String> entries = new ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(zipPath))) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                entries.add(entry.getName());
            }

        }

        assertEquals(2, entries.stream().distinct().count());
        assertTrue(entries.get(1).endsWith("-2.pdf"));
        assertTrue(entries.stream().allMatch(name ->
            name.getBytes(StandardCharsets.UTF_8).length <= 120
        ));
    }

    @Test
    void checksCancellationDuringChunkedZipWrites() throws Exception {
        Path source = temporaryDirectory.resolve("large.pdf");
        Files.write(source, new byte[256 * 1024]);
        Path zipPath = temporaryDirectory.resolve("cancelled.zip");
        java.util.concurrent.atomic.AtomicInteger checks =
            new java.util.concurrent.atomic.AtomicInteger();

        assertThrows(
            OperationCancelledException.class,
            () -> new ZipArtifactService().create(
                List.of(new OperationOutput(source, "large.pdf", "application/pdf")),
                zipPath,
                "cancelled.zip",
                1024 * 1024,
                () -> {
                    if (checks.incrementAndGet() > 2) {
                        throw new OperationCancelledException();
                    }
                },
                true
            )
        );

        assertFalse(Files.exists(zipPath));
        assertTrue(Files.exists(source));
    }
}
