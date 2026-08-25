package com.pdftools.operations.shared.worker;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class IsolatedJavaWorkerTest {

    @Test
    void pinsNativeJvmMemoryReservations() {
        List<String> command = IsolatedJavaWorker.command(
            new IsolatedJavaWorker.Spec(
                IsolatedJavaWorkerTest.class,
                512L * 1024L * 1024L,
                Duration.ofMinutes(1),
                "START_FAILED",
                "Worker start failed",
                "WORKER_TIMEOUT",
                "Worker timed out"
            ),
            List.of(),
            Path.of("/tmp")
        );

        assertTrue(command.contains("-XX:MaxMetaspaceSize=134217728"));
        assertTrue(command.contains(
            "-XX:CompressedClassSpaceSize=67108864"
        ));
        assertTrue(command.contains("-XX:ReservedCodeCacheSize=67108864"));
        assertTrue(command.contains("-Xss524288"));
    }
}
