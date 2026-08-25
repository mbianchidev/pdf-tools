package com.pdftools.operations.htmlpdf;

import com.pdftools.jobs.JobService;
import com.pdftools.jobs.JobStatus;
import com.pdftools.jobs.api.JobResponse;
import com.pdftools.jobs.persistence.JobOutputEntity;
import com.pdftools.jobs.persistence.JobOutputRepository;
import com.pdftools.operations.shared.queue.ConversionQueueProtocol;
import com.pdftools.storage.StorageService;
import com.pdftools.storage.StoredResource;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = "pdf.jobs.enabled-operations=html-to-pdf")
class HtmlToPdfJobIntegrationTest {

    private static Path queueRoot;

    @DynamicPropertySource
    static void queueProperties(DynamicPropertyRegistry registry) {
        registry.add(
            "pdf.operations.html.queue-request-root",
            () -> queueRoot.resolve("requests")
        );
        registry.add(
            "pdf.operations.html.queue-response-root",
            () -> queueRoot.resolve("responses")
        );
        registry.add(
            "pdf.operations.html.queue-signal-root",
            () -> queueRoot.resolve("signals")
        );
        registry.add(
            "pdf.operations.html.queue-wait-timeout",
            () -> "5s"
        );
        registry.add(
            "pdf.operations.html.wall-timeout",
            () -> "5s"
        );
    }

    @BeforeAll
    static void createQueue() throws Exception {
        queueRoot = Files.createTempDirectory("html-job-queue-");
    }

    @AfterAll
    static void removeQueue() throws Exception {
        if (queueRoot == null) {
            return;
        }
        try (var paths = Files.walk(queueRoot)) {
            for (Path path : paths
                    .sorted(Comparator.reverseOrder())
                    .toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    @Autowired
    private JobService jobService;

    @Autowired
    private JobOutputRepository outputRepository;

    @Autowired
    private StorageService storageService;

    @Test
    void convertsHtmlThroughPersistentJob() throws Exception {
        CompletableFuture<Void> worker = CompletableFuture.runAsync(
            this::completeNextRequest
        );
        MockMultipartFile input = new MockMultipartFile(
            "files",
            "report.html",
            "text/html",
            "<!doctype html><h1>Persistent HTML</h1>".getBytes(
                java.nio.charset.StandardCharsets.UTF_8
            )
        );

        JobResponse completed = awaitTerminal(jobService.create(
            "html-to-pdf",
            """
            {
              "pageSize":"letter",
              "orientation":"landscape",
              "marginMm":8
            }
            """,
            List.of(input)
        ));

        assertEquals(JobStatus.COMPLETED, completed.status());
        assertEquals("report.pdf", completed.outputs().getFirst().filename());
        JobOutputEntity output = outputRepository
            .findAllByJobIdOrderByPosition(completed.id())
            .getFirst();
        try (StoredResource resource = storageService.get(
                output.getStorageKey());
             PDDocument document = Loader.loadPDF(
                 resource.inputStream().readAllBytes()
             )) {
            assertEquals(1, document.getNumberOfPages());
        }
        worker.get(5, TimeUnit.SECONDS);
    }

    private void completeNextRequest() {
        long deadline = System.nanoTime()
            + java.time.Duration.ofSeconds(5).toNanos();
        while (System.nanoTime() < deadline) {
            try {
                Path requests = queueRoot.resolve("requests");
                if (Files.isDirectory(requests)) {
                    try (var paths = Files.list(requests)) {
                        Path request = paths
                            .filter(Files::isDirectory)
                            .findFirst()
                            .orElse(null);
                        if (request != null && Files.isRegularFile(
                                request.resolve(
                                    ConversionQueueProtocol.READY))) {
                            String requestId =
                                request.getFileName().toString();
                            Path response = queueRoot.resolve("responses")
                                .resolve(requestId);
                            Files.createDirectories(response);
                            Files.write(
                                response.resolve(
                                    ConversionQueueProtocol.OUTPUT),
                                pdf()
                            );
                            ConversionQueueProtocol.marker(
                                response.resolve(
                                    ConversionQueueProtocol.COMPLETED)
                            );
                            return;
                        }
                    }
                }
                Thread.sleep(20);
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
        }
        throw new AssertionError("HTML request was not queued");
    }

    private byte[] pdf() throws Exception {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(output);
            return output.toByteArray();
        }
    }

    private JobResponse awaitTerminal(JobResponse created) throws Exception {
        Instant deadline = Instant.now().plusSeconds(10);
        JobResponse current = created;
        while (!current.status().isTerminal()
                && Instant.now().isBefore(deadline)) {
            Thread.sleep(50);
            current = jobService.get(created.id());
        }
        assertTrue(
            current.status().isTerminal(),
            "HTML-to-PDF job did not complete"
        );
        return current;
    }
}
