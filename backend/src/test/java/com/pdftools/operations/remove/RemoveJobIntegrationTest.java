package com.pdftools.operations.remove;

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
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties = "pdf.jobs.enabled-operations=remove")
class RemoveJobIntegrationTest {

    @Autowired
    private JobService jobService;

    @Autowired
    private StorageService storageService;

    @Test
    void removesPagesThroughPersistentJobPipeline() throws Exception {
        MockMultipartFile input = new MockMultipartFile(
            "files",
            "source.pdf",
            "application/pdf",
            PdfTestFixtures.coloredPdfBytes(List.of(
                new PdfTestFixtures.PageSpec(100, 100, Color.RED),
                new PdfTestFixtures.PageSpec(200, 100, Color.GREEN),
                new PdfTestFixtures.PageSpec(300, 100, Color.BLUE)
            ))
        );

        JobResponse completed = awaitTerminal(jobService.create(
            "remove",
            "{\"pages\":\"2\"}",
            List.of(input)
        ));

        assertEquals(JobStatus.COMPLETED, completed.status());
        assertEquals(
            "source_pages_removed.pdf",
            completed.outputs().getFirst().filename()
        );
        JobOutputEntity output = jobService.getOutput(
            completed.id(),
            completed.outputs().getFirst().id()
        );
        try (StoredResource resource = storageService.get(
                output.getStorageKey());
             PDDocument document = Loader.loadPDF(
                 resource.inputStream().readAllBytes()
             )) {
            assertEquals(2, document.getNumberOfPages());
            assertEquals(100, document.getPage(0).getMediaBox().getWidth());
            assertEquals(300, document.getPage(1).getMediaBox().getWidth());
        }
    }

    private JobResponse awaitTerminal(JobResponse created) throws Exception {
        Instant deadline = Instant.now().plusSeconds(10);
        JobResponse current = created;
        while (!current.status().isTerminal()
                && Instant.now().isBefore(deadline)) {
            Thread.sleep(20);
            current = jobService.get(created.id());
        }
        if (!current.status().isTerminal()) {
            throw new AssertionError("Remove Pages job did not complete");
        }
        return current;
    }
}
