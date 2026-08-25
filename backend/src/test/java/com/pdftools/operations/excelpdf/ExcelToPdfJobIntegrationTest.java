package com.pdftools.operations.excelpdf;

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
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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
    "pdf.jobs.enabled-operations=excel-to-pdf",
    "pdf.operations.office.mode=direct",
    "pdf.operations.office.isolated-container=true"
})
class ExcelToPdfJobIntegrationTest {

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
    void convertsWorkbookThroughPersistentJob() throws Exception {
        MockMultipartFile input = new MockMultipartFile(
            "files",
            "workbook.xlsx",
            "application/vnd.openxmlformats-officedocument."
                + "spreadsheetml.sheet",
            workbook()
        );

        JobResponse completed = awaitTerminal(jobService.create(
            "excel-to-pdf",
            """
            {"printAreaMode":"used","orientation":"landscape"}
            """,
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
                    .contains("PERSISTENT WORKBOOK")
            );
            assertTrue(
                document.getPage(0).getMediaBox().getWidth()
                    > document.getPage(0).getMediaBox().getHeight()
            );
        }
    }

    private byte[] workbook() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            workbook.createSheet("Data")
                .createRow(0)
                .createCell(0)
                .setCellValue("PERSISTENT WORKBOOK");
            workbook.write(output);
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
            throw new AssertionError("Excel-to-PDF job did not complete");
        }
        return current;
    }
}
