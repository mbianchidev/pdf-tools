package com.pdftools.operations.excelpdf;

import com.pdftools.operations.OperationContext;
import com.pdftools.operations.OperationException;
import com.pdftools.operations.OperationInput;
import com.pdftools.operations.OperationOutput;
import com.pdftools.operations.OperationSubmission;
import com.pdftools.operations.office.LibreOfficeConverter;
import com.pdftools.operations.office.NativeProcessSandbox;
import com.pdftools.operations.office.OfficeConversionProperties;
import com.pdftools.operations.office.OfficeConversionQueueClient;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExcelToPdfOperationTest {

    @TempDir
    Path temporaryDirectory;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OfficeConversionProperties officeProperties =
        officeProperties();
    private final ExcelProperties excelProperties = new ExcelProperties();
    private final ExcelDocumentValidator validator =
        new ExcelDocumentValidator(officeProperties);
    private final ExcelPlanFactory planFactory = new ExcelPlanFactory();
    private final ExcelWorkbookPreparer preparer =
        new ExcelWorkbookPreparer(excelProperties);
    private final ExcelToPdfOperation operation = new ExcelToPdfOperation(
        new ExcelToPdfConverter(
            officeProperties,
            new LibreOfficeExcelConverter(
                validator,
                planFactory,
                new ExcelPreparationService(
                    excelProperties,
                    officeProperties,
                    new NativeProcessSandbox()
                ),
                new LibreOfficeConverter(
                    officeProperties,
                    new NativeProcessSandbox()
                )
            ),
            new OfficeConversionQueueClient(officeProperties)
        ),
        validator,
        planFactory
    );

    @Test
    void appliesCustomPrintAreaAndLandscapeToPdf() throws Exception {
        OperationOutput output = operation.execute(context(
            workbook(),
            """
            {
              "printAreaMode":"custom",
              "printArea":"A1:C10",
              "orientation":"landscape"
            }
            """
        )).getFirst();

        assertEquals("workbook.pdf", output.filename());
        try (PDDocument document = Loader.loadPDF(output.path().toFile())) {
            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("INSIDE AREA"));
            assertTrue(text.contains("SECOND SHEET"));
            assertFalse(text.contains("OUTSIDE AREA"));
            assertTrue(document.getNumberOfPages() >= 2);
            assertTrue(
                document.getPage(0).getMediaBox().getWidth()
                    > document.getPage(0).getMediaBox().getHeight()
            );
        }
    }

    @Test
    void appliesUsedRangesAndPortraitDuringPreparation() throws Exception {
        Path prepared = temporaryDirectory.resolve("prepared.xlsx");

        preparer.prepare(
            workbook(),
            prepared,
            planFactory.create(objectMapper.readTree("""
                {
                  "printAreaMode":"used",
                  "orientation":"portrait"
                }
                """), org.apache.poi.ss.SpreadsheetVersion.EXCEL2007),
            org.apache.poi.ss.SpreadsheetVersion.EXCEL2007,
            () -> {
            }
        );

        try (var workbook = WorkbookFactory.create(prepared.toFile())) {
            assertTrue(workbook.getPrintArea(0).contains("$A$1:$Z$100"));
            assertFalse(workbook.getSheetAt(0).getPrintSetup().getLandscape());
        }
    }

    @Test
    void validatesFilesAndPrintControls() throws Exception {
        operation.validateSubmission(new OperationSubmission(
            objectMapper.readTree("{}"),
            List.of(new OperationSubmission.UploadDescriptor(
                1,
                "workbook.xlsx",
                "application/vnd.openxmlformats-officedocument."
                    + "spreadsheetml.sheet",
                100
            ))
        ));
        assertSubmissionCode(
            "INVALID_EXCEL_FILE",
            "{}",
            new OperationSubmission.UploadDescriptor(
                1,
                "workbook.pptx",
                "application/octet-stream",
                100
            )
        );
        assertSubmissionCode(
            "INVALID_EXCEL_PRINT_AREA",
            """
            {
              "printAreaMode":"custom",
              "printArea":"Sheet1!A1:C5"
            }
            """,
            descriptor()
        );
        assertSubmissionCode(
            "INVALID_EXCEL_ORIENTATION",
            """
            {"orientation":"diagonal"}
            """,
            descriptor()
        );
    }

    private OfficeConversionProperties officeProperties() {
        OfficeConversionProperties configured =
            new OfficeConversionProperties();
        configured.setMode("direct");
        configured.setIsolatedContainer(true);
        configured.setWorkerUser(System.getProperty("user.name"));
        configured.setMaxWorkerProcesses(4096);
        configured.setLibreOfficeBinary(localSoffice());
        configured.setWallTimeout(Duration.ofMinutes(1));
        configured.setCpuTimeSeconds(60);
        return configured;
    }

    private String localSoffice() {
        for (String candidate : List.of(
                "/opt/homebrew/bin/soffice",
                "/Applications/LibreOffice.app/Contents/MacOS/soffice",
                "/usr/bin/soffice")) {
            if (Files.isExecutable(Path.of(candidate))) {
                return candidate;
            }
        }
        return "soffice";
    }

    private Path workbook() throws Exception {
        Path source = temporaryDirectory.resolve(
            "workbook-" + UUID.randomUUID() + ".xlsx"
        );
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            var first = workbook.createSheet("Data");
            first.createRow(0).createCell(0).setCellValue("INSIDE AREA");
            first.createRow(9).createCell(2).setCellValue("EDGE");
            first.createRow(99).createCell(25).setCellValue("OUTSIDE AREA");
            var second = workbook.createSheet("Summary");
            second.createRow(0).createCell(0).setCellValue("SECOND SHEET");
            try (var output = Files.newOutputStream(source)) {
                workbook.write(output);
            }
        }
        return source;
    }

    private OperationSubmission.UploadDescriptor descriptor() {
        return new OperationSubmission.UploadDescriptor(
            1,
            "workbook.xlsx",
            "application/vnd.openxmlformats-officedocument."
                + "spreadsheetml.sheet",
            100
        );
    }

    private void assertSubmissionCode(
            String code,
            String options,
            OperationSubmission.UploadDescriptor descriptor)
            throws Exception {
        OperationException exception = assertThrows(
            OperationException.class,
            () -> operation.validateSubmission(new OperationSubmission(
                objectMapper.readTree(options),
                List.of(descriptor)
            ))
        );
        assertEquals(code, exception.getCode());
    }

    private OperationContext context(Path source, String options)
            throws Exception {
        return new OperationContext(
            UUID.randomUUID(),
            objectMapper.readTree(options),
            List.of(new OperationInput(
                1,
                source,
                "workbook.xlsx",
                "application/vnd.openxmlformats-officedocument."
                    + "spreadsheetml.sheet",
                Files.size(source),
                "excel-source"
            )),
            Files.createTempDirectory(
                temporaryDirectory,
                "excel-pdf-context-"
            ),
            ignored -> {
            },
            () -> false
        );
    }
}
