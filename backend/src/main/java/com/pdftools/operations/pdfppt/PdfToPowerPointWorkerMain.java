package com.pdftools.operations.pdfppt;

import com.pdftools.operations.OperationException;
import com.pdftools.operations.shared.worker.IsolatedJavaWorker;

import java.nio.file.Path;

public final class PdfToPowerPointWorkerMain {

    private PdfToPowerPointWorkerMain() {
    }

    public static void main(String[] arguments) {
        if (arguments.length != 3) {
            System.err.println("Invalid PDF-to-PowerPoint worker arguments");
            System.exit(3);
        }
        Path errorFile = Path.of(arguments[2]);
        try {
            PdfToPowerPointRequest request =
                PdfToPowerPointRequest.read(Path.of(arguments[0]));
            new PdfToPowerPointWorker().convert(
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
                "PDF_POWERPOINT_WORKER_FAILED",
                "The isolated PDF-to-PowerPoint worker failed"
            );
            System.exit(3);
        }
    }
}
