package com.pdftools.operations.compare;

import com.pdftools.operations.OperationException;
import com.pdftools.operations.shared.worker.IsolatedJavaWorker;

import java.nio.file.Path;

public final class ComparePdfWorkerMain {

    private ComparePdfWorkerMain() {
    }

    public static void main(String[] arguments) {
        if (arguments.length != 3) {
            System.err.println("Invalid PDF comparison worker arguments");
            System.exit(3);
        }
        Path errorFile = Path.of(arguments[2]);
        try {
            CompareWorkerRequest request = CompareWorkerRequest.read(
                Path.of(arguments[0])
            );
            new ComparePdfWorker(request).compare(
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
                "PDF_COMPARE_WORKER_FAILED",
                "The isolated PDF comparison worker failed"
            );
            System.exit(3);
        }
    }
}
