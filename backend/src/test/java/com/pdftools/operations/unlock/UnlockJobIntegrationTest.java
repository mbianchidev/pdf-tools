package com.pdftools.operations.unlock;

import com.pdftools.jobs.JobService;
import com.pdftools.jobs.JobStatus;
import com.pdftools.jobs.api.JobResponse;
import com.pdftools.jobs.persistence.JobEntity;
import com.pdftools.jobs.persistence.JobOutputEntity;
import com.pdftools.jobs.persistence.JobOutputRepository;
import com.pdftools.jobs.persistence.JobRepository;
import com.pdftools.storage.StorageService;
import com.pdftools.storage.StoredResource;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = "pdf.jobs.enabled-operations=unlock")
class UnlockJobIntegrationTest {

    @Autowired
    private JobService jobService;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private JobOutputRepository outputRepository;

    @Autowired
    private StorageService storageService;

    @Test
    void unlocksPdfWithoutPersistingPlaintextPassword() throws Exception {
        MockMultipartFile input = new MockMultipartFile(
            "files",
            "source.pdf",
            "application/pdf",
            encryptedPdf()
        );

        JobResponse created = jobService.create(
            "unlock",
            """
            {"password":"open-secret"}
            """,
            List.of(input)
        );
        JobEntity storedJob = jobRepository.findById(created.id())
            .orElseThrow();
        assertTrue(storedJob.getOptionsJson().startsWith("enc:v1:"));
        assertFalse(storedJob.getOptionsJson().contains("open-secret"));

        JobResponse completed = awaitTerminal(created);
        assertTrue(completed.status() == JobStatus.COMPLETED);
        JobOutputEntity output = outputRepository
            .findAllByJobIdOrderByPosition(created.id())
            .getFirst();
        try (StoredResource resource = storageService.get(
                output.getStorageKey());
             PDDocument document = Loader.loadPDF(
                 resource.inputStream().readAllBytes()
             )) {
            assertFalse(document.isEncrypted());
            assertTrue(
                document.getCurrentAccessPermission().isOwnerPermission()
            );
        }
    }

    private byte[] encryptedPdf() throws Exception {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            StandardProtectionPolicy policy = new StandardProtectionPolicy(
                "owner-secret",
                "open-secret",
                new AccessPermission()
            );
            policy.setEncryptionKeyLength(256);
            policy.setPreferAES(true);
            document.protect(policy);
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
            throw new AssertionError("Unlock PDF job did not complete");
        }
        return current;
    }
}
