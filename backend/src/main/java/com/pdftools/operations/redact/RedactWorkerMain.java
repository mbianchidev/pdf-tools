package com.pdftools.operations.redact;

import com.pdftools.operations.OperationException;
import com.pdftools.operations.shared.worker.IsolatedJavaWorker;

import java.nio.file.Path;

public final class RedactWorkerMain {

    private static final int ARGUMENT_COUNT = 3;

    private RedactWorkerMain() {
    }

    public static void main(String[] arguments) {
        if (arguments.length != ARGUMENT_COUNT) {
            System.err.println("Invalid secure redaction worker arguments");
            System.exit(3);
        }
        Path errorFile = Path.of(arguments[2]);
        try {
            RedactWorkerRequest request = RedactWorkerRequest.read(
                Path.of(arguments[0])
            );
            new RedactWorker(request).redact(Path.of(arguments[1]));
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
                "REDACT_WORKER_FAILED",
                "The isolated secure redaction worker failed"
            );
            System.exit(3);
        }
    }
}
