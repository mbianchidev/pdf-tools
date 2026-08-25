package com.pdftools.operations.pptpdf;

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
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.ByteArrayOutputStream;
import java.awt.Dimension;
import java.awt.geom.Rectangle2D;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
    "pdf.jobs.enabled-operations=powerpoint-to-pdf",
    "pdf.operations.office.mode=direct",
    "pdf.operations.office.isolated-container=true"
})
class PowerPointToPdfJobIntegrationTest {

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
    void convertsPresentationThroughPersistentJob() throws Exception {
        MockMultipartFile input = new MockMultipartFile(
            "files",
            "slides.pptx",
            "application/vnd.openxmlformats-officedocument."
                + "presentationml.presentation",
            presentation()
        );

        JobResponse completed = awaitTerminal(jobService.create(
            "powerpoint-to-pdf",
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
                    .contains("PERSISTENT PRESENTATION")
            );
        }
    }

    private byte[] presentation() throws Exception {
        try (XMLSlideShow presentation = new XMLSlideShow();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            presentation.setPageSize(new Dimension(960, 540));
            var slide = presentation.createSlide();
            var text = slide.createTextBox();
            text.setAnchor(new Rectangle2D.Double(60, 40, 700, 80));
            var run = text.addNewTextParagraph().addNewTextRun();
            run.setText("PERSISTENT PRESENTATION");
            run.setFontSize(30.0);
            presentation.write(output);
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
            throw new AssertionError("PowerPoint-to-PDF job did not complete");
        }
        return current;
    }
}
