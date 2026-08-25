package com.pdftools.operations;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyOperationGuardTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void cancellationDeletesOwnedOutputAndStopsFurtherWork() throws Exception {
        Path output = Files.createFile(temporaryDirectory.resolve("output.zip"));
        LegacyOperationGuard guard = new LegacyOperationGuard();
        guard.own(output);

        guard.cancel();

        assertFalse(Files.exists(output));
        assertThrows(OperationCancelledException.class, guard::checkCancelled);
    }

    @Test
    void successfulCompletionLeavesPublishedOutput() throws Exception {
        Path output = Files.createFile(temporaryDirectory.resolve("output.zip"));
        LegacyOperationGuard guard = new LegacyOperationGuard();
        guard.own(output);

        guard.complete();

        assertTrue(Files.exists(output));
    }
}
