package com.pdftools.operations.office;

import com.pdftools.operations.OperationCancelledException;
import com.pdftools.operations.OperationException;
import com.pdftools.operations.OperationInput;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class OfficeConversionQueueClientTest {

    @TempDir
    Path temporaryDirectory;

    private OfficeConversionProperties properties;
    private OfficeConversionQueueClient client;

    @BeforeEach
    void setUp() {
        properties = properties();
        client = new OfficeConversionQueueClient(properties);
    }

    @Test
    void transfersCompletedOutputThroughNoFollowChannel() throws Exception {
        byte[] pdf = pdf();
        CompletableFuture<Void> worker = CompletableFuture.runAsync(() -> {
            RequestState state = awaitRequest();
            try {
                Files.write(
                    state.response().resolve(OfficeQueueProtocol.OUTPUT),
                    pdf
                );
                OfficeQueueProtocol.marker(
                    state.response().resolve(OfficeQueueProtocol.COMPLETED)
                );
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
        });

        Path output = client.convertWord(
            input(),
            temporaryDirectory.resolve("job"),
            ignored -> {
            },
            () -> {
            }
        );

        assertArrayEquals(pdf, Files.readAllBytes(output));
        worker.get(5, TimeUnit.SECONDS);
        assertFalse(hasRequestDirectories());
    }

    @Test
    void rejectsSymlinkedQueueOutput() throws Exception {
        Path outside = temporaryDirectory.resolve("outside.pdf");
        Files.write(outside, pdf());
        CompletableFuture<Void> worker = CompletableFuture.runAsync(() -> {
            RequestState state = awaitRequest();
            try {
                Files.createSymbolicLink(
                    state.response().resolve(OfficeQueueProtocol.OUTPUT),
                    outside
                );
                OfficeQueueProtocol.marker(
                    state.response().resolve(OfficeQueueProtocol.COMPLETED)
                );
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
        });

        OperationException failure = assertThrows(
            OperationException.class,
            () -> client.convertWord(
                input(),
                temporaryDirectory.resolve("symlink-job"),
                ignored -> {
                },
                () -> {
                }
            )
        );

        assertEquals("INVALID_WORD_PDF_OUTPUT", failure.getCode());
        worker.get(5, TimeUnit.SECONDS);
    }

    @Test
    void rejectsFifoQueueOutputWithoutBlocking() throws Exception {
        CompletableFuture<Void> worker = CompletableFuture.runAsync(() -> {
            RequestState state = awaitRequest();
            try {
                Process fifo = new ProcessBuilder(
                    "/usr/bin/mkfifo",
                    state.response()
                        .resolve(OfficeQueueProtocol.OUTPUT)
                        .toString()
                ).start();
                if (fifo.waitFor() != 0) {
                    throw new IllegalStateException("mkfifo failed");
                }
                OfficeQueueProtocol.marker(
                    state.response().resolve(OfficeQueueProtocol.COMPLETED)
                );
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
        });

        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
            OperationException failure = assertThrows(
                OperationException.class,
                () -> client.convertWord(
                    input(),
                    temporaryDirectory.resolve("fifo-job"),
                    ignored -> {
                    },
                    () -> {
                    }
                )
            );
            assertEquals("INVALID_WORD_PDF_OUTPUT", failure.getCode());
        });
        worker.get(5, TimeUnit.SECONDS);
    }

    @Test
    void signalsCancellationAndWaitsForDaemonTerminalState()
            throws Exception {
        CompletableFuture<Void> worker = CompletableFuture.runAsync(() -> {
            RequestState state = awaitRequest(false);
            Path cancel = properties.getQueueSignalRoot().resolve(
                state.requestId() + OfficeQueueProtocol.CANCEL
            );
            long deadline = System.nanoTime()
                + Duration.ofSeconds(3).toNanos();
            while (!Files.exists(cancel) && System.nanoTime() < deadline) {
                sleep();
            }
            OfficeQueueProtocol.writeFailure(
                state.response().resolve(OfficeQueueProtocol.FAILED),
                "OFFICE_CONVERSION_CANCELLED",
                "Office conversion was cancelled"
            );
        });

        assertThrows(
            OperationCancelledException.class,
            () -> client.convertWord(
                input(),
                temporaryDirectory.resolve("cancel-job"),
                ignored -> {
                },
                () -> {
                    throw new OperationCancelledException();
                }
            )
        );

        worker.get(5, TimeUnit.SECONDS);
        assertFalse(hasRequestDirectories());
    }

    private OfficeConversionProperties properties() {
        OfficeConversionProperties configured =
            new OfficeConversionProperties();
        configured.setMode("queue");
        configured.setQueueRequestRoot(
            temporaryDirectory.resolve("requests")
        );
        configured.setQueueResponseRoot(
            temporaryDirectory.resolve("responses")
        );
        configured.setQueueSignalRoot(
            temporaryDirectory.resolve("signals")
        );
        configured.setWallTimeout(Duration.ofSeconds(5));
        return configured;
    }

    private OperationInput input() throws Exception {
        Path source = temporaryDirectory.resolve(
            "source-" + UUID.randomUUID() + ".docx"
        );
        Files.write(source, new byte[]{1, 2, 3});
        Path workspace = temporaryDirectory.resolve("job");
        Files.createDirectories(workspace);
        return new OperationInput(
            1,
            source,
            "source.docx",
            "application/vnd.openxmlformats-officedocument."
                + "wordprocessingml.document",
            Files.size(source),
            "queue-source"
        );
    }

    private RequestState awaitRequest() {
        return awaitRequest(true);
    }

    private RequestState awaitRequest(boolean requireReady) {
        long deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos();
        while (System.nanoTime() < deadline) {
            try {
                if (Files.isDirectory(properties.getQueueRequestRoot())) {
                    try (var paths = Files.list(
                            properties.getQueueRequestRoot())) {
                        Path request = paths
                            .filter(Files::isDirectory)
                            .findFirst()
                            .orElse(null);
                        if (request != null
                                && (!requireReady || Files.exists(
                                    request.resolve(
                                        OfficeQueueProtocol.READY)))) {
                            String requestId =
                                request.getFileName().toString();
                            Path response = properties
                                .getQueueResponseRoot()
                                .resolve(requestId);
                            Files.createDirectories(response);
                            return new RequestState(
                                requestId,
                                request,
                                response
                            );
                        }
                    }
                }
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
            sleep();
        }
        throw new AssertionError("Queue request was not created");
    }

    private boolean hasRequestDirectories() throws Exception {
        if (!Files.isDirectory(properties.getQueueRequestRoot())) {
            return false;
        }
        try (var paths = Files.list(properties.getQueueRequestRoot())) {
            return paths.anyMatch(Files::isDirectory);
        }
    }

    private byte[] pdf() throws Exception {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(output);
            return output.toByteArray();
        }
    }

    private void sleep() {
        try {
            Thread.sleep(20);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(exception);
        }
    }

    private record RequestState(
        String requestId,
        Path request,
        Path response
    ) {
    }
}
