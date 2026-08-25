package com.pdftools.operations.pdfexcel;

import com.pdftools.operations.OperationException;
import com.pdftools.operations.shared.worker.IsolatedJavaWorker;

import java.nio.file.Path;

public final class PdfToExcelWorkerMain {

    private PdfToExcelWorkerMain() {
    }

    public static void main(String[] arguments) {
        if (arguments.length != 3) {
            System.err.println("Invalid PDF-to-Excel worker arguments");
            System.exit(3);
        }
        Path errorFile = Path.of(arguments[2]);
        try {
            PdfToExcelRequest request = PdfToExcelRequest.read(
                Path.of(arguments[0])
            );
            new PdfToExcelWorker().convert(
                request,
                Path.of(arguments[1])
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
                "PDF_EXCEL_WORKER_FAILED",
                "The isolated PDF-to-Excel worker failed"
            );
            System.exit(3);
        }
    }
}
