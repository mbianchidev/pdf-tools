package com.pdftools.operations.merge;

import com.pdftools.jobs.JobService;
import com.pdftools.jobs.JobStatus;
import com.pdftools.jobs.api.JobResponse;
import com.pdftools.jobs.persistence.JobOutputEntity;
import com.pdftools.storage.StorageService;
import com.pdftools.storage.StoredResource;
import com.pdftools.testing.PdfTestFixtures;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties = "pdf.jobs.enabled-operations=merge")
class MergeJobIntegrationTest {

    @Autowired
    private JobService jobService;

    @Autowired
    private StorageService storageService;

    @Test
    void runsMergeThroughThePersistentJobPipeline() throws Exception {
        MockMultipartFile first = pdf("first.pdf", 210, 297, Color.RED);
        MockMultipartFile second = pdf("second.pdf", 297, 210, Color.BLUE);

        JobResponse created = jobService.create(
            "merge",
            "{\"outputFilename\":\"combined.pdf\"}",
            List.of(first, second)
        );
        JobResponse completed = awaitTerminal(created);

        assertEquals(JobStatus.COMPLETED, completed.status());
        assertEquals("combined.pdf", completed.outputs().getFirst().filename());
        JobOutputEntity output = jobService.getOutput(
            completed.id(),
            completed.outputs().getFirst().id()
        );
        try (StoredResource resource = storageService.get(output.getStorageKey());
             ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            resource.inputStream().transferTo(bytes);
            try (PDDocument merged = Loader.loadPDF(bytes.toByteArray())) {
                assertEquals(2, merged.getNumberOfPages());
                assertEquals(210, merged.getPage(0).getMediaBox().getWidth());
                assertEquals(297, merged.getPage(1).getMediaBox().getWidth());
            }
        }
    }

    private MockMultipartFile pdf(
            String filename,
            float width,
            float height,
            Color color) throws Exception {
        return new MockMultipartFile(
            "files",
            filename,
            "application/pdf",
            PdfTestFixtures.coloredPdfBytes(
                List.of(new PdfTestFixtures.PageSpec(width, height, color))
            )
        );
    }

    private JobResponse awaitTerminal(JobResponse created) throws Exception {
        Instant deadline = Instant.now().plusSeconds(10);
        JobResponse current = created;
        while (!current.status().isTerminal() && Instant.now().isBefore(deadline)) {
            Thread.sleep(20);
            current = jobService.get(created.id());
        }
        if (!current.status().isTerminal()) {
            throw new AssertionError("Merge job did not complete");
        }
        return current;
    }
}
