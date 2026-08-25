package com.pdftools.operations.pdfexcel;

import com.pdftools.operations.BoundedOutputStream;
import com.pdftools.operations.OperationException;
import com.pdftools.operations.OutputLimitExceededException;
import com.pdftools.operations.shared.extraction.AlignedTableDetector;
import com.pdftools.operations.shared.extraction.AlignedTableDetector.TableCandidate;
import com.pdftools.operations.shared.extraction.PdfPageContent;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

final class PdfToExcelWorkbookWriter {

    private final PdfToExcelProperties properties;
    private final AlignedTableDetector tableDetector;
    private long cells;
    private int tables;

    PdfToExcelWorkbookWriter(PdfToExcelProperties properties) {
        this.properties = properties;
        this.tableDetector = new AlignedTableDetector(
            properties.getMaxColumns()
        );
    }

    void write(
            List<PdfPageContent> pages,
            PdfToExcelPlanFactory.PdfToExcelPlan plan,
            Path output,
            Runnable progress) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Styles styles = styles(workbook);
            int written = plan.sheetMode().equals("pages")
                ? writePageSheets(workbook, pages, plan, styles, progress)
                : writeTableSheets(workbook, pages, styles, progress);
            if (written == 0 && plan.sheetMode().equals("tables")) {
                throw new OperationException(
                    "NO_PDF_TABLES_FOUND",
                    "No aligned tables were detected in the PDF"
                );
            }
            writeWorkbook(workbook, output);
        } catch (OperationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new OperationException(
                "PDF_EXCEL_WRITE_FAILED",
                "The Excel workbook could not be generated",
                exception
            );
        }
    }

    private int writePageSheets(
            XSSFWorkbook workbook,
            List<PdfPageContent> pages,
            PdfToExcelPlanFactory.PdfToExcelPlan plan,
            Styles styles,
            Runnable progress) {
        if (pages.size() > properties.getMaxSheets()) {
            throw sheetLimit();
        }
        int totalWritten = 0;
        for (int pageIndex = 0;
                pageIndex < pages.size();
                pageIndex++) {
            Sheet sheet = workbook.createSheet("Page " + (pageIndex + 1));
            int[] widths = new int[properties.getMaxColumns()];
            int rowIndex = 0;
            List<PdfPageContent.TextLine> lines =
                pages.get(pageIndex).lines();
            int lineIndex = 0;
            while (lineIndex < lines.size()) {
                TableCandidate table = tableDetector.tableAt(
                    lines,
                    lineIndex
                );
                if (table != null) {
                    rowIndex = writeTable(
                        sheet,
                        rowIndex,
                        table,
                        styles,
                        widths
                    );
                    totalWritten += table.rows().size();
                    lineIndex += table.rows().size();
                } else {
                    if (plan.includeNonTableText()) {
                        Row row = row(sheet, rowIndex++);
                        writeValue(
                            cell(row, 0),
                            lines.get(lineIndex).text(),
                            styles.body()
                        );
                        trackWidth(
                            widths,
                            0,
                            lines.get(lineIndex).text()
                        );
                        totalWritten++;
                    }
                    lineIndex++;
                }
            }
            applyWidths(sheet, widths);
            progress.run();
        }
        return totalWritten;
    }

    private int writeTableSheets(
            XSSFWorkbook workbook,
            List<PdfPageContent> pages,
            Styles styles,
            Runnable progress) {
        int writtenTables = 0;
        for (PdfPageContent page : pages) {
            int lineIndex = 0;
            while (lineIndex < page.lines().size()) {
                TableCandidate table = tableDetector.tableAt(
                    page.lines(),
                    lineIndex
                );
                if (table == null) {
                    lineIndex++;
                    continue;
                }
                writtenTables++;
                if (writtenTables > properties.getMaxTables()
                        || writtenTables > properties.getMaxSheets()) {
                    throw sheetLimit();
                }
                Sheet sheet = workbook.createSheet(
                    "Table " + writtenTables
                );
                int[] widths = new int[properties.getMaxColumns()];
                writeTable(sheet, 0, table, styles, widths);
                applyWidths(sheet, widths);
                sheet.createFreezePane(0, 1);
                lineIndex += table.rows().size();
            }
            progress.run();
        }
        return writtenTables;
    }

    private int writeTable(
            Sheet sheet,
            int rowIndex,
            TableCandidate table,
            Styles styles,
            int[] widths) {
        tables++;
        if (tables > properties.getMaxTables()) {
            throw sheetLimit();
        }
        for (int tableRow = 0;
                tableRow < table.rows().size();
                tableRow++) {
            Row row = row(sheet, rowIndex++);
            List<AlignedTableDetector.Cell> values =
                table.rows().get(tableRow);
            if (values.size() > properties.getMaxColumns()) {
                throw columnLimit();
            }
            for (int column = 0; column < values.size(); column++) {
                String value = values.get(column).text();
                writeValue(
                    cell(row, column),
                    value,
                    tableRow == 0 ? styles.header() : styles.body()
                );
                trackWidth(widths, column, value);
            }
        }
        return rowIndex;
    }

    private Row row(Sheet sheet, int rowIndex) {
        if (rowIndex >= properties.getMaxRowsPerSheet()) {
            throw new OperationException(
                "PDF_EXCEL_ROW_LIMIT_EXCEEDED",
                "A generated worksheet exceeds the row limit"
            );
        }
        return sheet.createRow(rowIndex);
    }

    private Cell cell(Row row, int column) {
        if (column >= properties.getMaxColumns()) {
            throw columnLimit();
        }
        try {
            cells = Math.addExact(cells, 1);
        } catch (ArithmeticException exception) {
            throw cellLimit();
        }
        if (cells > properties.getMaxCells()) {
            throw cellLimit();
        }
        return row.createCell(column);
    }

    private void writeValue(
            Cell cell,
            String source,
            CellStyle style) {
        String value = source.strip();
        Double number = numericValue(value);
        if (number != null) {
            cell.setCellValue(number);
        } else {
            cell.setCellValue(value);
        }
        cell.setCellStyle(style);
    }

    private Double numericValue(String value) {
        if (!value.matches("[-+]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)")) {
            return null;
        }
        String unsigned = value.replaceFirst("^[-+]", "");
        if (unsigned.matches("0\\d.*")) {
            return null;
        }
        try {
            java.math.BigDecimal decimal =
                new java.math.BigDecimal(value);
            double number = decimal.doubleValue();
            if (!Double.isFinite(number)
                    || java.math.BigDecimal.valueOf(number)
                        .compareTo(decimal) != 0) {
                return null;
            }
            return number;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private void trackWidth(int[] widths, int column, String value) {
        widths[column] = Math.max(
            widths[column],
            Math.min(value.length() + 2, 60)
        );
    }

    private void applyWidths(Sheet sheet, int[] widths) {
        for (int column = 0; column < widths.length; column++) {
            if (widths[column] > 0) {
                sheet.setColumnWidth(
                    column,
                    Math.min(widths[column] * 256, 255 * 256)
                );
            }
        }
    }

    private Styles styles(XSSFWorkbook workbook) {
        var font = workbook.createFont();
        font.setBold(true);
        CellStyle header = workbook.createCellStyle();
        header.setFont(font);
        header.setFillForegroundColor(
            IndexedColors.LAVENDER.getIndex()
        );
        header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        borders(header);
        CellStyle body = workbook.createCellStyle();
        borders(body);
        return new Styles(header, body);
    }

    private void borders(CellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
    }

    private void writeWorkbook(
            XSSFWorkbook workbook,
            Path output) throws IOException {
        try (OutputStream fileOutput = Files.newOutputStream(output);
             BoundedOutputStream bounded = new BoundedOutputStream(
                 fileOutput,
                 properties.getMaxOutputBytes(),
                 () -> {
                 }
             )) {
            workbook.write(bounded);
        } catch (OutputLimitExceededException exception) {
            throw new OperationException(
                "PDF_EXCEL_OUTPUT_LIMIT_EXCEEDED",
                "The Excel workbook exceeds the output limit",
                exception
            );
        }
    }

    private OperationException sheetLimit() {
        return new OperationException(
            "PDF_EXCEL_SHEET_LIMIT_EXCEEDED",
            "The generated workbook exceeds the sheet or table limit"
        );
    }

    private OperationException columnLimit() {
        return new OperationException(
            "PDF_EXCEL_COLUMN_LIMIT_EXCEEDED",
            "A detected table exceeds the column limit"
        );
    }

    private OperationException cellLimit() {
        return new OperationException(
            "PDF_EXCEL_CELL_LIMIT_EXCEEDED",
            "The generated workbook exceeds the cell limit"
        );
    }

    private record Styles(CellStyle header, CellStyle body) {
    }
}
