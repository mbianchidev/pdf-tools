package com.pdftools.operations.protect;

import com.pdftools.jobs.JobService;
import com.pdftools.jobs.JobStatus;
import com.pdftools.jobs.api.JobResponse;
import com.pdftools.jobs.persistence.JobOutputEntity;
import com.pdftools.jobs.persistence.JobOutputRepository;
import com.pdftools.jobs.persistence.JobRepository;
import com.pdftools.storage.StorageService;
import com.pdftools.storage.StoredResource;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = "pdf.jobs.enabled-operations=protect")
class ProtectJobIntegrationTest {

    @Autowired
    private JobService jobService;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private JobOutputRepository outputRepository;

    @Autowired
    private StorageService storageService;

    @Test
    void protectsPdfWithoutPersistingPlaintextPasswords() throws Exception {
        MockMultipartFile input = new MockMultipartFile(
            "files",
            "source.pdf",
            "application/pdf",
            pdf()
        );

        JobResponse created = jobService.create(
            "protect",
            """
            {
              "userPassword":"open-secret",
              "ownerPassword":"owner-secret",
              "permissions":{"print":"none","copy":false}
            }
            """,
            List.of(input)
        );
        String storedOptions = jobRepository.findById(created.id())
            .orElseThrow()
            .getOptionsJson();
        assertTrue(storedOptions.startsWith("enc:v1:"));
        assertFalse(storedOptions.contains("open-secret"));
        assertFalse(storedOptions.contains("owner-secret"));

        JobResponse completed = awaitTerminal(created);
        assertTrue(completed.status() == JobStatus.COMPLETED);
        JobOutputEntity output = outputRepository
            .findAllByJobIdOrderByPosition(created.id())
            .getFirst();
        try (StoredResource resource = storageService.get(
                output.getStorageKey());
             PDDocument document = Loader.loadPDF(
                 resource.inputStream().readAllBytes(),
                 "open-secret"
             )) {
            assertTrue(document.isEncrypted());
            assertFalse(
                document.getCurrentAccessPermission().canExtractContent()
            );
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

    private JobResponse awaitTerminal(JobResponse created) throws Exception {
        Instant deadline = Instant.now().plusSeconds(10);
        JobResponse current = created;
        while (!current.status().isTerminal()
                && Instant.now().isBefore(deadline)) {
            Thread.sleep(20);
            current = jobService.get(created.id());
        }
        if (!current.status().isTerminal()) {
            throw new AssertionError("Protect PDF job did not complete");
        }
        return current;
    }
}
