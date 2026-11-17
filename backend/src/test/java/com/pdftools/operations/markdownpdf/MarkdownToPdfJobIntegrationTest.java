package com.pdftools.operations.markdownpdf;

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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = "pdf.jobs.enabled-operations=markdown-to-pdf")
class MarkdownToPdfJobIntegrationTest {

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
        registry.add("pdf.operations.html.queue-wait-timeout", () -> "10s");
        registry.add("pdf.operations.html.wall-timeout", () -> "10s");
    }

    @BeforeAll
    static void createQueue() throws Exception {
        queueRoot = Files.createTempDirectory("markdown-job-queue-");
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
    void rendersMarkdownThroughIsolatedWorkerAndHtmlQueue() throws Exception {
        CompletableFuture<String> sidecar = CompletableFuture.supplyAsync(
            this::completeNextRequest
        );
        MockMultipartFile input = new MockMultipartFile(
            "files",
            "release-notes.md",
            "text/markdown",
            """
            # Release notes

            <script>alert(1)</script>

            - [x] Shipped
            """.getBytes(StandardCharsets.UTF_8)
        );

        JobResponse completed = awaitTerminal(jobService.create(
            "markdown-to-pdf",
            "{\"pageSize\":\"letter\",\"marginMm\":12}",
            List.of(input)
        ));

        assertEquals(JobStatus.COMPLETED, completed.status());
        assertEquals(
            "release-notes.pdf",
            completed.outputs().getFirst().filename()
        );
        String queuedHtml = sidecar.get(5, TimeUnit.SECONDS);
        assertTrue(queuedHtml.contains("<h1 id=\"release-notes\">"));
        assertTrue(queuedHtml.contains("&lt;script&gt;"));
        assertFalse(queuedHtml.contains("<script"));
        assertSinglePagePdf(completed);
    }

    @Test
    void rendersZipBundleWithInlinedImages() throws Exception {
        CompletableFuture<String> sidecar = CompletableFuture.supplyAsync(
            this::completeNextRequest
        );
        MockMultipartFile input = new MockMultipartFile(
            "files",
            "document-markdown.zip",
            "application/zip",
            bundle()
        );

        JobResponse completed = awaitTerminal(jobService.create(
            "markdown-to-pdf",
            "{\"outputFilename\":\"bundle.pdf\"}",
            List.of(input)
        ));

        assertEquals(JobStatus.COMPLETED, completed.status());
        assertEquals("bundle.pdf", completed.outputs().getFirst().filename());
        String queuedHtml = sidecar.get(5, TimeUnit.SECONDS);
        assertTrue(queuedHtml.contains("src=\"data:image/png;base64,"));
        assertFalse(queuedHtml.contains("images/page-001-image-001.png"));
    }

    private void assertSinglePagePdf(JobResponse completed) throws Exception {
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
    }

    private byte[] bundle() throws Exception {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("document.md"));
            zip.write(
                "# Bundle\n\n![chart](images/page-001-image-001.png)\n"
                    .getBytes(StandardCharsets.UTF_8)
            );
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("images/page-001-image-001.png"));
            zip.write(MarkdownToHtmlRendererTest.PNG);
            zip.closeEntry();
            zip.finish();
            return bytes.toByteArray();
        }
    }

    private String completeNextRequest() {
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while (System.nanoTime() < deadline) {
            try {
                Path requests = queueRoot.resolve("requests");
                if (Files.isDirectory(requests)) {
                    try (var paths = Files.list(requests)) {
                        Path request = paths
                            .filter(Files::isDirectory)
                            .filter(path -> Files.isRegularFile(
                                path.resolve(ConversionQueueProtocol.READY)
                            ))
                            .findFirst()
                            .orElse(null);
                        if (request != null) {
                            String html = Files.readString(request.resolve(
                                ConversionQueueProtocol.INPUT + ".html"
                            ));
                            assertTrue(ConversionQueueProtocol.readRequest(
                                request.resolve(ConversionQueueProtocol.REQUEST)
                            ).optionsJson().endsWith(",\"renderMath\":true}"));
                            Path response = queueRoot.resolve("responses")
                                .resolve(request.getFileName().toString());
                            Files.createDirectories(response);
                            Files.write(
                                response.resolve(
                                    ConversionQueueProtocol.OUTPUT),
                                pdf()
                            );
                            ConversionQueueProtocol.marker(response.resolve(
                                ConversionQueueProtocol.COMPLETED
                            ));
                            return html;
                        }
                    }
                }
                Thread.sleep(20);
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
        }
        throw new AssertionError("Markdown HTML request was not queued");
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
        Instant deadline = Instant.now().plusSeconds(30);
        JobResponse current = created;
        while (!current.status().isTerminal()
                && Instant.now().isBefore(deadline)) {
            Thread.sleep(50);
            current = jobService.get(created.id());
        }
        assertTrue(
            current.status().isTerminal(),
            "Markdown-to-PDF job did not complete"
        );
        return current;
    }
}
