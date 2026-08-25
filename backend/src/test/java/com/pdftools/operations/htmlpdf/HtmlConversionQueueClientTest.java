package com.pdftools.operations.htmlpdf;

import com.pdftools.operations.OperationInput;
import com.pdftools.operations.shared.queue.ConversionQueueProtocol;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HtmlConversionQueueClientTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void transfersOptionsAndCompletedPdf() throws Exception {
        HtmlProperties properties = properties();
        HtmlConversionQueueClient client =
            new HtmlConversionQueueClient(properties);
        byte[] pdf = pdf();
        CompletableFuture<Void> worker = CompletableFuture.runAsync(() -> {
            Path request = awaitRequest(properties);
            ConversionQueueProtocol.Request queued =
                ConversionQueueProtocol.readRequest(
                    request.resolve(ConversionQueueProtocol.REQUEST)
                );
            assertEquals("html", queued.type());
            assertEquals(".html", queued.extension());
            assertEquals(
                "{\"pageSize\":\"A4\",\"landscape\":true,"
                    + "\"printBackground\":true,\"marginMm\":12}",
                queued.optionsJson()
            );
            try {
                String requestId = request.getFileName().toString();
                Path response = properties.getQueueResponseRoot()
                    .resolve(requestId);
                Files.createDirectories(response);
                Files.write(
                    response.resolve(ConversionQueueProtocol.OUTPUT),
                    pdf
                );
                ConversionQueueProtocol.marker(
                    response.resolve(ConversionQueueProtocol.COMPLETED)
                );
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }

        });

        Path output = client.convert(
            input(),
            new HtmlPlanFactory.HtmlPlan("A4", true, true, 12),
            temporaryDirectory.resolve("job"),
            ignored -> {
            },
            () -> {
            }
        );

        assertArrayEquals(pdf, Files.readAllBytes(output));
        worker.get(5, TimeUnit.SECONDS);
        assertFalse(hasRequestDirectories(properties));
    }

    @Test
    void rejectsPdfBeyondConfiguredPageLimit() throws Exception {
        HtmlProperties properties = properties();
        properties.setMaxPages(1);
        HtmlConversionQueueClient client =
            new HtmlConversionQueueClient(properties);
        CompletableFuture<Void> worker = CompletableFuture.runAsync(() -> {
            Path request = awaitRequest(properties);
            try {
                String requestId = request.getFileName().toString();
                Path response = properties.getQueueResponseRoot()
                    .resolve(requestId);
                Files.createDirectories(response);
                Files.write(
                    response.resolve(ConversionQueueProtocol.OUTPUT),
                    pdf(2)
                );
                ConversionQueueProtocol.marker(
                    response.resolve(ConversionQueueProtocol.COMPLETED)
                );
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
        });

        var exception = assertThrows(
            com.pdftools.operations.OperationException.class,
            () -> client.convert(
                input(),
                new HtmlPlanFactory.HtmlPlan("A4", false, true, 10),
                temporaryDirectory.resolve("job"),
                ignored -> {
                },
                () -> {
                }
            )
        );

        assertEquals("INVALID_HTML_PDF_OUTPUT", exception.getCode());
        worker.get(5, TimeUnit.SECONDS);
    }

    private HtmlProperties properties() {
        HtmlProperties configured = new HtmlProperties();
        configured.setQueueRequestRoot(
            temporaryDirectory.resolve("requests")
        );
        configured.setQueueResponseRoot(
            temporaryDirectory.resolve("responses")
        );
        configured.setQueueSignalRoot(
            temporaryDirectory.resolve("signals")
        );
        configured.setQueueWaitTimeout(Duration.ofSeconds(5));
        configured.setWallTimeout(Duration.ofSeconds(5));
        return configured;
    }

    private OperationInput input() throws Exception {
        Path source = temporaryDirectory.resolve("source.html");
        Files.writeString(source, "<!doctype html><p>Queue</p>");
        Path workspace = temporaryDirectory.resolve("job");
        Files.createDirectories(workspace);
        return new OperationInput(
            1,
            source,
            "source.html",
            "text/html",
            Files.size(source),
            "html-queue-source"
        );
    }

    private Path awaitRequest(HtmlProperties properties) {
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
                        if (request != null && Files.exists(
                                request.resolve(
                                    ConversionQueueProtocol.READY))) {
                            return request;
                        }
                    }
                }
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
            sleep();
        }
        throw new AssertionError("HTML queue request was not created");
    }

    private boolean hasRequestDirectories(HtmlProperties properties)
            throws Exception {
        try (var paths = Files.list(properties.getQueueRequestRoot())) {
            return paths.anyMatch(Files::isDirectory);
        }
    }

    private byte[] pdf() throws Exception {
        return pdf(1);
    }

    private byte[] pdf(int pages) throws Exception {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (int index = 0; index < pages; index++) {
                document.addPage(new PDPage());
            }
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
}
