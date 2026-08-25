package com.pdftools.operations.pdfexcel;

import com.pdftools.operations.OperationContext;
import com.pdftools.operations.OperationException;
import com.pdftools.operations.OperationInput;
import com.pdftools.operations.OperationOutput;
import com.pdftools.operations.OperationSubmission;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfToExcelOperationTest {

    @TempDir
    Path temporaryDirectory;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PdfToExcelProperties properties =
        new PdfToExcelProperties();
    private final PdfToExcelPlanFactory planFactory =
        new PdfToExcelPlanFactory();
    private final PdfToExcelOperation operation = new PdfToExcelOperation(
        new PdfToExcelEngine(properties),
        planFactory,
        properties
    );

    @Test
    void createsOneMixedContentSheetPerPage() throws Exception {
        OperationOutput output = operation.execute(context(
            tablePdf(),
            """
            {"sheetMode":"pages","includeNonTableText":true}
            """
        )).getFirst();

        assertEquals("report.xlsx", output.filename());
        try (XSSFWorkbook workbook = new XSSFWorkbook(
                Files.newInputStream(output.path()))) {
            assertEquals(2, workbook.getNumberOfSheets());
            assertEquals(
                "Quarterly Report",
                workbook.getSheetAt(0).getRow(0).getCell(0)
                    .getStringCellValue()
            );
            assertEquals(
                "Name",
                workbook.getSheetAt(0).getRow(1).getCell(0)
                    .getStringCellValue()
            );
            assertEquals(
                CellType.NUMERIC,
                workbook.getSheetAt(0).getRow(2).getCell(1)
                    .getCellType()
            );
            assertEquals(
                100,
                workbook.getSheetAt(0).getRow(2).getCell(1)
                    .getNumericCellValue()
            );
            assertEquals(
                "012.3",
                workbook.getSheetAt(0).getRow(3).getCell(1)
                    .getStringCellValue()
            );
            assertEquals(
                "00.5",
                workbook.getSheetAt(0).getRow(4).getCell(1)
                    .getStringCellValue()
            );
            assertEquals(
                "9007199254740993",
                workbook.getSheetAt(0).getRow(5).getCell(1)
                    .getStringCellValue()
            );
            assertTrue(workbook.getSheetAt(1).getRow(0).getCell(0)
                .getStringCellValue().contains("SECOND PAGE"));
        }
    }

    @Test
    void createsOneSheetPerDetectedTable() throws Exception {
        OperationOutput output = operation.execute(context(
            tablePdf(),
            """
            {"sheetMode":"tables"}
            """
        )).getFirst();

        try (XSSFWorkbook workbook = new XSSFWorkbook(
                Files.newInputStream(output.path()))) {
            assertEquals(1, workbook.getNumberOfSheets());
            assertEquals("Table 1", workbook.getSheetAt(0).getSheetName());
            assertEquals(
                "Revenue",
                workbook.getSheetAt(0).getRow(1).getCell(0)
                    .getStringCellValue()
            );
        }
    }

    @Test
    void rejectsTableModeWithoutTablesAndEncryptedPdf() throws Exception {
        OperationException noTables = assertThrows(
            OperationException.class,
            () -> operation.execute(context(
                textPdf(),
                "{\"sheetMode\":\"tables\"}"
            ))
        );
        assertEquals("NO_PDF_TABLES_FOUND", noTables.getCode());

        Path encrypted = temporaryDirectory.resolve("encrypted.pdf");
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            document.protect(
                new org.apache.pdfbox.pdmodel.encryption
                    .StandardProtectionPolicy(
                        "owner",
                        "user",
                        new org.apache.pdfbox.pdmodel.encryption
                            .AccessPermission()
                    )
            );
            document.save(encrypted.toFile());
        }
        OperationException encryptedFailure = assertThrows(
            OperationException.class,
            () -> operation.execute(context(encrypted, "{}"))
        );
        assertEquals(
            "ENCRYPTED_PDF_NOT_SUPPORTED",
            encryptedFailure.getCode()
        );
    }

    @Test
    void validatesSubmissionAndPageLimit() throws Exception {
        operation.validateSubmission(new OperationSubmission(
            objectMapper.readTree("{}"),
            List.of(descriptor("report.pdf", "application/pdf", 100))
        ));
        OperationException invalid = assertThrows(
            OperationException.class,
            () -> operation.validateSubmission(new OperationSubmission(
                objectMapper.readTree("{}"),
                List.of(descriptor("report.txt", "text/plain", 100))
            ))
        );
        assertEquals("INVALID_PDF_FILE", invalid.getCode());

        PdfToExcelProperties limited = new PdfToExcelProperties();
        limited.setMaxPages(1);
        PdfToExcelOperation limitedOperation = new PdfToExcelOperation(
            new PdfToExcelEngine(limited),
            planFactory,
            limited
        );
        OperationException pageLimit = assertThrows(
            OperationException.class,
            () -> limitedOperation.execute(context(tablePdf(), "{}"))
        );
        assertEquals("PDF_PAGE_LIMIT_EXCEEDED", pageLimit.getCode());
    }

    @Test
    void keepsEmptyPageSheetsWhenTextIsExcluded() throws Exception {
        OperationOutput output = operation.execute(context(
            textPdf(),
            """
            {
              "sheetMode":"pages",
              "includeNonTableText":false
            }
            """
        )).getFirst();

        try (XSSFWorkbook workbook = new XSSFWorkbook(
                Files.newInputStream(output.path()))) {
            assertEquals(1, workbook.getNumberOfSheets());
            assertEquals(0, workbook.getSheetAt(0).getPhysicalNumberOfRows());
        }
    }

    private Path tablePdf() throws Exception {
        Path source = temporaryDirectory.resolve(
            "tables-" + UUID.randomUUID() + ".pdf"
        );
        try (PDDocument document = new PDDocument()) {
            PDPage first = new PDPage();
            document.addPage(first);
            try (PDPageContentStream stream =
                     new PDPageContentStream(document, first)) {
                write(stream, 18, 50, 730, "Quarterly Report");
                writeRow(stream, 680, "Name", "Value");
                writeRow(stream, 650, "Revenue", "100");
                writeRow(stream, 620, "Code", "012.3");
                writeRow(stream, 590, "Ratio", "00.5");
                writeRow(
                    stream,
                    560,
                    "Identifier",
                    "9007199254740993"
                );
            }
            PDPage second = new PDPage();
            document.addPage(second);
            try (PDPageContentStream stream =
                     new PDPageContentStream(document, second)) {
                write(stream, 12, 50, 730, "SECOND PAGE NOTES");
            }
            document.save(source.toFile());
        }
        return source;
    }

    private Path textPdf() throws Exception {
        Path source = temporaryDirectory.resolve(
            "text-" + UUID.randomUUID() + ".pdf"
        );
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream stream =
                     new PDPageContentStream(document, page)) {
                write(stream, 12, 50, 700, "NO TABLE HERE");
            }
            document.save(source.toFile());
        }
        return source;
    }

    private void writeRow(
            PDPageContentStream stream,
            float y,
            String first,
            String second) throws Exception {
        write(stream, 12, 50, y, first);
        write(stream, 12, 112, y, second);
    }

    private void write(
            PDPageContentStream stream,
            float size,
            float x,
            float y,
            String text) throws Exception {
        stream.beginText();
        stream.setFont(
            new PDType1Font(Standard14Fonts.FontName.HELVETICA),
            size
        );
        stream.newLineAtOffset(x, y);
        stream.showText(text);
        stream.endText();
    }

    private OperationContext context(Path source, String options)
            throws Exception {
        return new OperationContext(
            UUID.randomUUID(),
            objectMapper.readTree(options),
            List.of(new OperationInput(
                1,
                source,
                "report.pdf",
                "application/pdf",
                Files.size(source),
                "pdf-excel-source"
            )),
            Files.createTempDirectory(
                temporaryDirectory,
                "pdf-excel-context-"
            ),
            ignored -> {
            },
            () -> false
        );
    }

    private OperationSubmission.UploadDescriptor descriptor(
            String filename,
            String mediaType,
            long size) {
        return new OperationSubmission.UploadDescriptor(
            1,
            filename,
            mediaType,
            size
        );
    }
}
