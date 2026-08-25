package com.pdftools.operations.excelpdf;

import com.pdftools.operations.BoundedOutputStream;
import com.pdftools.operations.OperationCancelledException;
import com.pdftools.operations.OperationException;
import com.pdftools.operations.OutputLimitExceededException;
import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.ss.SpreadsheetVersion;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.AreaReference;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

@Component
public class ExcelWorkbookPreparer {

    private final ExcelProperties properties;

    public ExcelWorkbookPreparer(ExcelProperties properties) {
        this.properties = properties;
    }

    public Path prepare(
            Path source,
            Path destination,
            ExcelPlanFactory.ExcelPlan plan,
            SpreadsheetVersion spreadsheetVersion,
            Runnable cancellationCheck) {
        Path workingCopy = destination.resolveSibling(
            ".excel-preparation-source"
        );
        try (InputStream input = Files.newInputStream(source);
             OutputStream output = Files.newOutputStream(
                 workingCopy,
                 StandardOpenOption.CREATE,
                 StandardOpenOption.TRUNCATE_EXISTING,
                 StandardOpenOption.WRITE
             )) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                cancellationCheck.run();
                output.write(buffer, 0, read);
            }
        } catch (IOException exception) {
            try {
                Files.deleteIfExists(workingCopy);
            } catch (IOException cleanupException) {
                exception.addSuppressed(cleanupException);
            }
            throw new OperationException(
                "EXCEL_PREPARATION_COPY_FAILED",
                "The workbook could not be copied into isolated scratch",
                exception
            );
        }
        try (Workbook workbook = WorkbookFactory.create(
                workingCopy.toFile())) {
            cancellationCheck.run();
            if (workbook.getNumberOfSheets() < 1
                    || workbook.getNumberOfSheets()
                        > properties.getMaxSheets()) {
                throw new OperationException(
                    "EXCEL_SHEET_LIMIT_EXCEEDED",
                    "The workbook has an unsupported number of sheets"
                );
            }
            AreaReference custom = plan.printAreaMode().equals("custom")
                ? new AreaReference(
                    plan.printArea(),
                    spreadsheetVersion
                )
                : null;
            long usedCells = 0;
            for (int index = 0;
                    index < workbook.getNumberOfSheets();
                    index++) {
                cancellationCheck.run();
                Sheet sheet = workbook.getSheetAt(index);
                applyOrientation(sheet, plan.orientation());
                UsedRange range = usedRange(
                    sheet,
                    usedCells,
                    cancellationCheck
                );
                usedCells = range.totalCells();
                if (plan.printAreaMode().equals("custom")) {
                    workbook.setPrintArea(
                        index,
                        custom.getFirstCell().getCol(),
                        custom.getLastCell().getCol(),
                        custom.getFirstCell().getRow(),
                        custom.getLastCell().getRow()
                    );
                } else if (plan.printAreaMode().equals("used")) {
                    if (range.empty()) {
                        workbook.removePrintArea(index);
                    } else {
                        workbook.setPrintArea(
                            index,
                            range.firstColumn(),
                            range.lastColumn(),
                            range.firstRow(),
                            range.lastRow()
                        );
                    }
                }
            }
            write(workbook, destination, cancellationCheck);
            return destination;
        } catch (OperationException | OperationCancelledException exception) {
            throw exception;
        } catch (EncryptedDocumentException exception) {
            throw new OperationException(
                "ENCRYPTED_EXCEL_DOCUMENT",
                "Password-protected Excel workbooks are not supported",
                exception
            );
        } catch (IOException | RuntimeException exception) {
            throw new OperationException(
                "INVALID_EXCEL_DOCUMENT",
                "The Excel workbook could not be prepared",
                exception
            );
        } finally {
            try {
                Files.deleteIfExists(workingCopy);
            } catch (IOException exception) {
                throw new OperationException(
                    "EXCEL_PREPARATION_CLEANUP_FAILED",
                    "Excel preparation scratch could not be removed",
                    exception
                );
            }
        }
    }

    private void applyOrientation(Sheet sheet, String orientation) {
        if (orientation.equals("portrait")) {
            sheet.getPrintSetup().setLandscape(false);
        } else if (orientation.equals("landscape")) {
            sheet.getPrintSetup().setLandscape(true);
        }
    }

    private UsedRange usedRange(
            Sheet sheet,
            long currentCells,
            Runnable cancellationCheck) {
        int firstRow = Integer.MAX_VALUE;
        int lastRow = -1;
        int firstColumn = Integer.MAX_VALUE;
        int lastColumn = -1;
        long cells = currentCells;
        for (Row row : sheet) {
            cancellationCheck.run();
            for (Cell cell : row) {
                try {
                    cells = Math.addExact(cells, 1);
                } catch (ArithmeticException exception) {
                    throw cellLimit();
                }
                if (cells > properties.getMaxUsedCells()) {
                    throw cellLimit();
                }
                if (cell.getCellType() == CellType.BLANK) {
                    continue;
                }
                firstRow = Math.min(firstRow, cell.getRowIndex());
                lastRow = Math.max(lastRow, cell.getRowIndex());
                firstColumn = Math.min(
                    firstColumn,
                    cell.getColumnIndex()
                );
                lastColumn = Math.max(lastColumn, cell.getColumnIndex());
            }
        }
        return new UsedRange(
            firstRow,
            lastRow,
            firstColumn,
            lastColumn,
            cells
        );
    }

    private OperationException cellLimit() {
        return new OperationException(
            "EXCEL_CELL_LIMIT_EXCEEDED",
            "The workbook exceeds the configured cell limit"
        );
    }

    private void write(
            Workbook workbook,
            Path destination,
            Runnable cancellationCheck) throws IOException {
        try (OutputStream fileOutput = Files.newOutputStream(destination);
             BoundedOutputStream bounded = new BoundedOutputStream(
                 fileOutput,
                 properties.getMaxPreparedBytes(),
                 cancellationCheck
             )) {
            workbook.write(bounded);
        } catch (OutputLimitExceededException exception) {
            throw new OperationException(
                "EXCEL_PREPARED_SIZE_LIMIT_EXCEEDED",
                "The prepared workbook exceeds the configured limit",
                exception
            );
        }
    }

    private record UsedRange(
        int firstRow,
        int lastRow,
        int firstColumn,
        int lastColumn,
        long totalCells
    ) {
        private boolean empty() {
            return lastRow < 0 || lastColumn < 0;
        }
    }
}
