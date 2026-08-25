package com.pdftools.operations.excelpdf;

import com.pdftools.operations.OperationException;
import com.pdftools.operations.office.OfficeConversionProperties;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.ss.SpreadsheetVersion;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExcelValidationTest {

    @TempDir
    Path temporaryDirectory;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExcelPlanFactory planFactory = new ExcelPlanFactory();

    @Test
    void enforcesXlsAndXlsxRangeBounds() throws Exception {
        planFactory.create(
            custom("A1:IV65536"),
            SpreadsheetVersion.EXCEL97
        );
        planFactory.create(
            custom("A1:XFD1048576"),
            SpreadsheetVersion.EXCEL2007
        );

        assertPlanCode(
            "INVALID_EXCEL_PRINT_AREA",
            "A1:IW1",
            SpreadsheetVersion.EXCEL97
        );
        assertPlanCode(
            "INVALID_EXCEL_PRINT_AREA",
            "A1:XFE1",
            SpreadsheetVersion.EXCEL2007
        );
    }

    @Test
    void classifiesEncryptedXlsxBeforeOoxmlValidation() throws Exception {
        Path source = temporaryDirectory.resolve("encrypted.xlsx");
        try (POIFSFileSystem filesystem = new POIFSFileSystem();
             var output = Files.newOutputStream(source)) {
            filesystem.getRoot().createDocument(
                "EncryptionInfo",
                new ByteArrayInputStream(new byte[]{1})
            );
            filesystem.getRoot().createDocument(
                "EncryptedPackage",
                new ByteArrayInputStream(new byte[]{2})
            );
            filesystem.writeFilesystem(output);
        }
        ExcelDocumentValidator validator = new ExcelDocumentValidator(
            new OfficeConversionProperties()
        );

        OperationException exception = assertThrows(
            OperationException.class,
            () -> validator.validate(source, "encrypted.xlsx", () -> {
            })
        );

        assertEquals("ENCRYPTED_EXCEL_DOCUMENT", exception.getCode());
    }

    @Test
    void appliesCellLimitInExistingAndCustomModes() throws Exception {
        Path source = xlsxWithTwoCells();
        ExcelProperties properties = new ExcelProperties();
        properties.setMaxUsedCells(1);
        ExcelWorkbookPreparer preparer =
            new ExcelWorkbookPreparer(properties);

        for (String options : new String[]{
                """
                {"printAreaMode":"existing"}
                """,
                """
                {
                  "printAreaMode":"custom",
                  "printArea":"A1:A1"
                }
                """}) {
            OperationException exception = assertThrows(
                OperationException.class,
                () -> preparer.prepare(
                    source,
                    temporaryDirectory.resolve(
                        "limited-" + options.hashCode() + ".xlsx"
                    ),
                    planFactory.create(
                        objectMapper.readTree(options),
                        SpreadsheetVersion.EXCEL2007
                    ),
                    SpreadsheetVersion.EXCEL2007,
                    () -> {
                    }
                )
            );
            assertEquals("EXCEL_CELL_LIMIT_EXCEEDED", exception.getCode());
        }
    }

    @Test
    void leavesReadOnlyQueueInputUnchanged() throws Exception {
        Path source = xlsxWithTwoCells();
        byte[] original = Files.readAllBytes(source);
        Set<PosixFilePermission> permissions =
            Files.getPosixFilePermissions(source);
        Files.setPosixFilePermissions(
            source,
            Set.of(PosixFilePermission.OWNER_READ)
        );
        try {
            new ExcelWorkbookPreparer(new ExcelProperties()).prepare(
                source,
                temporaryDirectory.resolve("prepared.xlsx"),
                planFactory.create(
                    objectMapper.readTree(
                        "{\"printAreaMode\":\"used\"}"
                    ),
                    SpreadsheetVersion.EXCEL2007
                ),
                SpreadsheetVersion.EXCEL2007,
                () -> {
                }
            );
        } finally {
            Files.setPosixFilePermissions(source, permissions);
        }

        assertArrayEquals(original, Files.readAllBytes(source));
    }

    @Test
    void reportsLegacySpreadsheetVersionFromFilename() {
        assertEquals(
            SpreadsheetVersion.EXCEL97,
            planFactory.spreadsheetVersion("workbook.XLS")
        );
        assertEquals(
            SpreadsheetVersion.EXCEL2007,
            planFactory.spreadsheetVersion("workbook.xlsx")
        );
    }

    private tools.jackson.databind.JsonNode custom(String range)
            throws Exception {
        return objectMapper.readTree(
            "{\"printAreaMode\":\"custom\",\"printArea\":\""
                + range + "\"}"
        );
    }

    private void assertPlanCode(
            String code,
            String range,
            SpreadsheetVersion version) throws Exception {
        OperationException exception = assertThrows(
            OperationException.class,
            () -> planFactory.create(custom(range), version)
        );
        assertEquals(code, exception.getCode());
    }

    private Path xlsxWithTwoCells() throws Exception {
        Path source = temporaryDirectory.resolve(
            "source-" + java.util.UUID.randomUUID() + ".xlsx"
        );
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             var output = Files.newOutputStream(source)) {
            var row = workbook.createSheet("Data").createRow(0);
            row.createCell(0).setCellValue("one");
            row.createCell(1).setCellValue("two");
            workbook.write(output);
        }
        return source;
    }
}
