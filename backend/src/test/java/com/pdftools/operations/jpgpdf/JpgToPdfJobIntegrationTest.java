package com.pdftools.operations.jpgpdf;

import com.pdftools.jobs.JobService;
import com.pdftools.jobs.JobStatus;
import com.pdftools.jobs.api.JobResponse;
import com.pdftools.jobs.persistence.JobOutputEntity;
import com.pdftools.jobs.persistence.JobOutputRepository;
import com.pdftools.storage.StorageService;
import com.pdftools.storage.StoredResource;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties = "pdf.jobs.enabled-operations=jpg-to-pdf")
class JpgToPdfJobIntegrationTest {

    @Autowired
    private JobService jobService;

    @Autowired
    private JobOutputRepository outputRepository;

    @Autowired
    private StorageService storageService;

    @Test
    void convertsOrderedJpegsThroughPersistentJob() throws Exception {
        MockMultipartFile first = image("first.jpg", Color.RED);
        MockMultipartFile second = image("second.jpg", Color.BLUE);

        JobResponse completed = awaitTerminal(jobService.create(
            "jpg-to-pdf",
            """
            {"pageSize":"a4","orientation":"portrait","margin":24}
            """,
            List.of(first, second)
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
            assertEquals(2, document.getNumberOfPages());
        }
    }

    private MockMultipartFile image(String name, Color color)
            throws Exception {
        BufferedImage image = new BufferedImage(
            20,
            10,
            BufferedImage.TYPE_INT_RGB
        );
        java.awt.Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(color);
            graphics.fillRect(0, 0, 20, 10);
        } finally {
            graphics.dispose();
        }
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "jpeg", output);
            return new MockMultipartFile(
                "files",
                name,
                "image/jpeg",
                output.toByteArray()
            );
        } finally {
            image.flush();
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
            throw new AssertionError("JPG to PDF job did not complete");
        }
        return current;
    }
}
