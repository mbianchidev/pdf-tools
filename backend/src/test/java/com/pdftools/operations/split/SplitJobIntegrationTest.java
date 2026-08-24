package com.pdftools.operations.split;

import com.pdftools.jobs.JobService;
import com.pdftools.jobs.JobStatus;
import com.pdftools.jobs.api.JobResponse;
import com.pdftools.jobs.persistence.JobOutputEntity;
import com.pdftools.storage.StorageService;
import com.pdftools.storage.StoredResource;
import com.pdftools.testing.PdfTestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.List;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties = "pdf.jobs.enabled-operations=split")
class SplitJobIntegrationTest {

    @Autowired
    private JobService jobService;

    @Autowired
    private StorageService storageService;

    @Test
    void runsFixedSplitThroughThePersistentJobPipeline() throws Exception {
        MockMultipartFile input = new MockMultipartFile(
            "files",
            "source.pdf",
            "application/pdf",
            PdfTestFixtures.coloredPdfBytes(List.of(
                new PdfTestFixtures.PageSpec(100, 100, Color.RED),
                new PdfTestFixtures.PageSpec(100, 100, Color.GREEN),
                new PdfTestFixtures.PageSpec(100, 100, Color.BLUE)
            ))
        );

        JobResponse completed = awaitTerminal(jobService.create(
            "split",
            "{\"mode\":\"fixed\",\"fixedGroupSize\":2}",
            List.of(input)
        ));

        assertEquals(JobStatus.COMPLETED, completed.status());
        assertEquals("source_split.zip", completed.outputs().getFirst().filename());
        JobOutputEntity output = jobService.getOutput(
            completed.id(),
            completed.outputs().getFirst().id()
        );
        try (StoredResource resource = storageService.get(output.getStorageKey());
             ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            resource.inputStream().transferTo(bytes);
            java.nio.file.Path zipPath = java.nio.file.Files.createTempFile("split-job-", ".zip");
            try {
                java.nio.file.Files.write(zipPath, bytes.toByteArray());
                try (ZipFile archive = new ZipFile(zipPath.toFile())) {
                    assertEquals(2, archive.size());
                }
            } finally {
                java.nio.file.Files.deleteIfExists(zipPath);
            }
        }
    }

    private JobResponse awaitTerminal(JobResponse created) throws Exception {
        Instant deadline = Instant.now().plusSeconds(10);
        JobResponse current = created;
        while (!current.status().isTerminal() && Instant.now().isBefore(deadline)) {
            Thread.sleep(20);
            current = jobService.get(created.id());
        }
        if (!current.status().isTerminal()) {
            throw new AssertionError("Split job did not complete");
        }
        return current;
    }
}
