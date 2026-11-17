package com.pdftools.operations.markdownpdf;

import com.pdftools.operations.OperationException;
import com.pdftools.operations.shared.worker.IsolatedJavaWorker;

import java.nio.file.Path;

public final class MarkdownToHtmlWorkerMain {

    private MarkdownToHtmlWorkerMain() {
    }

    public static void main(String[] arguments) {
        if (arguments.length != 2) {
            System.err.println("Invalid Markdown worker arguments");
            System.exit(3);
        }
        Path errorFile = Path.of(arguments[1]);
        try {
            new MarkdownToHtmlWorker().convert(
                MarkdownToHtmlRequest.read(Path.of(arguments[0]))
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
                "MARKDOWN_WORKER_FAILED",
                "The isolated Markdown worker failed"
            );
            System.exit(3);
        }
    }
}
