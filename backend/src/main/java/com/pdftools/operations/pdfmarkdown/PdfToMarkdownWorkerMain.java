package com.pdftools.operations.pdfmarkdown;

import com.pdftools.operations.OperationException;
import com.pdftools.operations.shared.worker.IsolatedJavaWorker;

import java.nio.file.Path;

public final class PdfToMarkdownWorkerMain {

    private PdfToMarkdownWorkerMain() {
    }

    public static void main(String[] arguments) {
        if (arguments.length != 3) {
            System.err.println("Invalid PDF-to-Markdown worker arguments");
            System.exit(3);
        }
        Path errorFile = Path.of(arguments[2]);
        try {
            PdfToMarkdownRequest request = PdfToMarkdownRequest.read(
                Path.of(arguments[0])
            );
            new PdfToMarkdownWorker().convert(
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
                "PDF_MARKDOWN_WORKER_FAILED",
                "The isolated PDF-to-Markdown worker failed"
            );
            System.exit(3);
        }
    }
}
