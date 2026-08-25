package com.pdftools.operations.repair;

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
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = "pdf.jobs.enabled-operations=repair")
class RepairPdfJobIntegrationTest {

    @Autowired
    private JobService jobService;

    @Autowired
    private JobOutputRepository outputRepository;

    @Autowired
    private StorageService storageService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void repairsPdfAndPersistsReport() throws Exception {
        MockMultipartFile input = new MockMultipartFile(
            "files",
            "persistent.pdf",
            "application/pdf",
            pdf()
        );

        JobResponse completed = awaitTerminal(jobService.create(
            "repair",
            "{}",
            List.of(input)
        ));

        assertEquals(JobStatus.COMPLETED, completed.status());
        assertEquals(2, completed.outputs().size());
        assertEquals(
            "persistent-repaired.pdf",
            completed.outputs().getFirst().filename()
        );
        assertEquals(
            "persistent-repair-report.json",
            completed.outputs().get(1).filename()
        );
        List<JobOutputEntity> outputs = outputRepository
            .findAllByJobIdOrderByPosition(completed.id());
        try (StoredResource resource = storageService.get(
                outputs.get(1).getStorageKey())) {
            var report = objectMapper.readTree(resource.inputStream());
            assertEquals(
                "repaired",
                report.path("status").asText(),
                report::toPrettyString
            );
        }
    }

    private byte[] pdf() throws Exception {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
             PDPage page = new PDPage();
             document.addPage(page);
             try (PDPageContentStream stream =
                      new PDPageContentStream(document, page)) {
                 stream.beginText();
                 stream.setFont(
                     new PDType1Font(
                         Standard14Fonts.FontName.HELVETICA
                     ),
                     12
                 );
                 stream.newLineAtOffset(50, 700);
                 stream.showText("REPAIR JOB");
                 stream.endText();
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
            "PDF repair job did not complete"
        );
        return current;
    }
}
