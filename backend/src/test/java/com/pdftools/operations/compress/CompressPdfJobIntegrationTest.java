package com.pdftools.operations.compress;

import com.pdftools.jobs.JobService;
import com.pdftools.jobs.JobStatus;
import com.pdftools.jobs.api.JobResponse;
import com.pdftools.jobs.persistence.JobOutputEntity;
import com.pdftools.jobs.persistence.JobOutputRepository;
import com.pdftools.storage.StorageService;
import com.pdftools.storage.StoredResource;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = "pdf.jobs.enabled-operations=compress")
class CompressPdfJobIntegrationTest {

    @Autowired
    private JobService jobService;

    @Autowired
    private JobOutputRepository outputRepository;

    @Autowired
    private StorageService storageService;

    @Test
    void compressesPdfThroughPersistentJob() throws Exception {
        byte[] inputBytes = imagePdf();
        MockMultipartFile input = new MockMultipartFile(
            "files",
            "persistent.pdf",
            "application/pdf",
            inputBytes
        );

        JobResponse completed = awaitTerminal(jobService.create(
            "compress",
            "{\"mode\":\"extreme\"}",
            List.of(input)
        ));

        assertEquals(JobStatus.COMPLETED, completed.status());
        assertEquals(
            "persistent-compressed.pdf",
            completed.outputs().getFirst().filename()
        );
        assertTrue(
            completed.outputs().getFirst().sizeBytes()
                < inputBytes.length
        );
        JobOutputEntity output = outputRepository
            .findAllByJobIdOrderByPosition(completed.id())
            .getFirst();
        try (StoredResource resource = storageService.get(
                output.getStorageKey())) {
            assertEquals(
                "%PDF",
                new String(
                    resource.inputStream().readNBytes(4),
                    StandardCharsets.US_ASCII
                )
            );
        }
    }

    private byte[] imagePdf() throws Exception {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            BufferedImage image = new BufferedImage(
                600,
                400,
                BufferedImage.TYPE_INT_RGB
            );
            int noise = 0x2468ace1;
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    noise ^= noise << 13;
                    noise ^= noise >>> 17;
                    noise ^= noise << 5;
                    image.setRGB(x, y, noise & 0x00ffffff);
                }
            }
            try (PDPageContentStream stream =
                     new PDPageContentStream(document, page)) {
                stream.drawImage(
                    LosslessFactory.createFromImage(document, image),
                    20,
                    20,
                    500,
                    400
                );
            } finally {
                image.flush();
            }
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
            "Compression job did not complete"
        );
        return current;
    }
}
