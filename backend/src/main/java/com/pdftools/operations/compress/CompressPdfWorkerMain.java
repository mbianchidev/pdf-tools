package com.pdftools.operations.compress;

import com.pdftools.operations.OperationException;
import com.pdftools.operations.shared.worker.IsolatedJavaWorker;

import java.nio.file.Path;

public final class CompressPdfWorkerMain {

    private CompressPdfWorkerMain() {
    }

    public static void main(String[] arguments) {
        if (arguments.length != 3) {
            System.err.println("Invalid PDF compression worker arguments");
            System.exit(3);
        }
        Path errorFile = Path.of(arguments[2]);
        try {
            CompressPdfRequest request = CompressPdfRequest.read(
                Path.of(arguments[0])
            );
            new CompressPdfWorker(request).compress(
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
                "COMPRESS_WORKER_FAILED",
                "The isolated PDF compression worker failed"
            );
            System.exit(3);
        }
    }
}
