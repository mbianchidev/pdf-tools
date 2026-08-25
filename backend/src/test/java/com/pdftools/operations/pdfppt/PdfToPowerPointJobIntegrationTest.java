package com.pdftools.operations.pdfppt;

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
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
    properties = "pdf.jobs.enabled-operations=pdf-to-powerpoint"
)
class PdfToPowerPointJobIntegrationTest {

    @Autowired
    private JobService jobService;

    @Autowired
    private JobOutputRepository outputRepository;

    @Autowired
    private StorageService storageService;

    @Test
    void convertsPdfThroughPersistentJob() throws Exception {
        MockMultipartFile input = new MockMultipartFile(
            "files",
            "persistent.pdf",
            "application/pdf",
            pdf()
        );

        JobResponse completed = awaitTerminal(jobService.create(
            "pdf-to-powerpoint",
            """
            {"mode":"editable","slideSize":"source"}
            """,
            List.of(input)
        ));

        assertEquals(JobStatus.COMPLETED, completed.status());
        assertEquals(
            "persistent.pptx",
            completed.outputs().getFirst().filename()
        );
        JobOutputEntity output = outputRepository
            .findAllByJobIdOrderByPosition(completed.id())
            .getFirst();
        try (StoredResource resource = storageService.get(
                output.getStorageKey());
             XMLSlideShow presentation = new XMLSlideShow(
                 resource.inputStream())) {
            assertEquals(1, presentation.getSlides().size());
            assertTrue(presentation.getSlides().getFirst()
                .getShapes().stream()
                .filter(XSLFTextShape.class::isInstance)
                .map(XSLFTextShape.class::cast)
                .anyMatch(shape -> shape.getText()
                    .contains("PERSISTENT POWERPOINT")));
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
                    new PDType1Font(Standard14Fonts.FontName.HELVETICA),
                    12
                );
                stream.newLineAtOffset(50, 700);
                stream.showText("PERSISTENT POWERPOINT");
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
            "PDF-to-PowerPoint job did not complete"
        );
        return current;
    }
}
