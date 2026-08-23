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
}
