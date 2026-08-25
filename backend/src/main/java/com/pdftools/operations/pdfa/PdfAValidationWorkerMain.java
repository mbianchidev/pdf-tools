package com.pdftools.operations.pdfa;

import com.pdftools.operations.OperationException;
import com.pdftools.operations.shared.worker.IsolatedJavaWorker;
import org.verapdf.gf.foundry.VeraGreenfieldFoundryProvider;

import java.nio.file.Path;

public final class PdfAValidationWorkerMain {

    private PdfAValidationWorkerMain() {
    }

    public static void main(String[] arguments) {
        if (arguments.length != 2) {
            System.err.println("Invalid veraPDF worker arguments");
            System.exit(3);
        }
        Path errorFile = Path.of(arguments[1]);
        try {
            VeraGreenfieldFoundryProvider.initialise();
            PdfAValidationRequest request = PdfAValidationRequest.read(
                Path.of(arguments[0])
            );
            new PdfAValidationWorker().validate(request);
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
                "PDFA_VALIDATOR_FAILED",
                "The isolated veraPDF validator failed"
            );
            System.exit(3);
        }
    }
}
