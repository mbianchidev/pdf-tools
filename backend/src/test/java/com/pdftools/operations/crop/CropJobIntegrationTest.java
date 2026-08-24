package com.pdftools.operations.crop;

import com.pdftools.jobs.JobService;
import com.pdftools.jobs.JobStatus;
import com.pdftools.jobs.api.JobResponse;
import com.pdftools.jobs.persistence.JobOutputEntity;
import com.pdftools.storage.StorageService;
import com.pdftools.storage.StoredResource;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties = "pdf.jobs.enabled-operations=crop")
class CropJobIntegrationTest {

    @Autowired
    private JobService jobService;

    @Autowired
    private StorageService storageService;

    @Test
    void cropsPagesThroughPersistentJobPipeline() throws Exception {
        MockMultipartFile input = new MockMultipartFile(
            "files",
            "source.pdf",
            "application/pdf",
            pdf()
        );

        JobResponse completed = awaitTerminal(jobService.create(
            "crop",
            """
            {
              "crop":{"x":0.1,"y":0.2,"width":0.5,"height":0.5}
            }
            """,
            List.of(input)
        ));

        assertEquals(JobStatus.COMPLETED, completed.status());
        assertEquals(
            "source_cropped.pdf",
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
            PDRectangle box = document.getPage(0).getCropBox();
            assertEquals(10, box.getLowerLeftX(), 0.001);
            assertEquals(30, box.getLowerLeftY(), 0.001);
            assertEquals(50, box.getWidth(), 0.001);
            assertEquals(50, box.getHeight(), 0.001);
        }
    }

    private byte[] pdf() throws Exception {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage(new PDRectangle(100, 100)));
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
            throw new AssertionError("Crop PDF job did not complete");
        }
        return current;
    }
}
