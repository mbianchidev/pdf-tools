package com.pdftools.operations.pagenumbers;

import com.pdftools.jobs.JobService;
import com.pdftools.jobs.JobStatus;
import com.pdftools.jobs.api.JobResponse;
import com.pdftools.jobs.persistence.JobOutputEntity;
import com.pdftools.storage.StorageService;
import com.pdftools.storage.StoredResource;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = "pdf.jobs.enabled-operations=page-numbers")
class PageNumbersJobIntegrationTest {

    @Autowired
    private JobService jobService;

    @Autowired
    private StorageService storageService;

    @Test
    void numbersPagesThroughPersistentJobPipeline() throws Exception {
        MockMultipartFile input = new MockMultipartFile(
            "files",
            "source.pdf",
            "application/pdf",
            pdf()
        );

        JobResponse completed = awaitTerminal(jobService.create(
            "page-numbers",
            """
            {
              "pages":"2-3",
              "start":5,
              "template":"Page {page} of {total}",
              "font":"helvetica",
              "fontSize":12,
              "position":"bottom-center",
              "margin":12
            }
            """,
            List.of(input)
        ));

        assertEquals(JobStatus.COMPLETED, completed.status());
        assertEquals(
            "source_numbered.pdf",
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
            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("Page 5 of 3"));
            assertTrue(text.contains("Page 6 of 3"));
        }
    }

    private byte[] pdf() throws Exception {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.addPage(new PDPage());
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
            Thread.sleep(20);
            current = jobService.get(created.id());
        }
        if (!current.status().isTerminal()) {
            throw new AssertionError("Page Numbers job did not complete");
        }
        return current;
    }
}
