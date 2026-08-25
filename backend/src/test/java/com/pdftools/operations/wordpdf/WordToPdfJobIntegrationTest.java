package com.pdftools.operations.wordpdf;

import com.pdftools.jobs.JobService;
import com.pdftools.jobs.JobStatus;
import com.pdftools.jobs.api.JobResponse;
import com.pdftools.jobs.persistence.JobOutputEntity;
import com.pdftools.jobs.persistence.JobOutputRepository;
import com.pdftools.storage.StorageService;
import com.pdftools.storage.StoredResource;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
    "pdf.jobs.enabled-operations=word-to-pdf",
    "pdf.operations.office.mode=direct",
    "pdf.operations.office.isolated-container=true"
})
class WordToPdfJobIntegrationTest {

    @DynamicPropertySource
    static void officeWorker(DynamicPropertyRegistry registry) {
        registry.add(
            "pdf.operations.office.worker-user",
            () -> System.getProperty("user.name")
        );
        registry.add(
            "pdf.operations.office.max-worker-processes",
            () -> 4096
        );
    }

    @Autowired
    private JobService jobService;

    @Autowired
    private JobOutputRepository outputRepository;

    @Autowired
    private StorageService storageService;

    @Test
    void convertsWordDocumentThroughPersistentJob() throws Exception {
        MockMultipartFile input = new MockMultipartFile(
            "files",
            "source.docx",
            "application/vnd.openxmlformats-officedocument."
                + "wordprocessingml.document",
            docx()
        );

        JobResponse completed = awaitTerminal(jobService.create(
            "word-to-pdf",
            "{}",
            List.of(input)
        ));

        assertEquals(JobStatus.COMPLETED, completed.status());
        JobOutputEntity output = outputRepository
            .findAllByJobIdOrderByPosition(completed.id())
            .getFirst();
        try (StoredResource resource = storageService.get(
                output.getStorageKey());
             PDDocument document = Loader.loadPDF(
                 resource.inputStream().readAllBytes()
             )) {
            assertTrue(
                new PDFTextStripper().getText(document)
                    .contains("PERSISTENT WORD CONVERSION")
            );
        }
    }

    private byte[] docx() throws Exception {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createParagraph()
                .createRun()
                .setText("PERSISTENT WORD CONVERSION");
            document.write(output);
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
        if (!current.status().isTerminal()) {
            throw new AssertionError("Word-to-PDF job did not complete");
        }
        return current;
    }
}
