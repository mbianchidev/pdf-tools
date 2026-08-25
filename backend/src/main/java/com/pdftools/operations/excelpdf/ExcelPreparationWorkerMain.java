package com.pdftools.operations.excelpdf;

import com.pdftools.operations.OperationException;
import com.pdftools.operations.shared.worker.IsolatedJavaWorker;

import java.nio.file.Path;

public final class ExcelPreparationWorkerMain {

    private ExcelPreparationWorkerMain() {
    }

    public static void main(String[] arguments) {
        if (arguments.length != 2) {
            System.err.println("Invalid Excel preparation worker arguments");
            System.exit(3);
        }
        Path errorFile = Path.of(arguments[1]);
        try {
            ExcelPreparationRequest request =
                ExcelPreparationRequest.read(Path.of(arguments[0]));
            new ExcelWorkbookPreparer(request.properties()).prepare(
                request.source(),
                request.destination(),
                request.plan(),
                request.spreadsheetVersion(),
                () -> {
                }
            );
        } catch (OperationException exception) {
            IsolatedJavaWorker.writeError(
                errorFile,
                exception.getCode(),
                exception.getMessage()
            );
            System.exit(2);
        } catch (Throwable throwable) {
            throwable.printStackTrace(System.err);
            IsolatedJavaWorker.writeError(
                errorFile,
                "EXCEL_PREPARATION_FAILED",
                "The isolated Excel preparation worker failed"
            );
            System.exit(3);
        }
    }
}
