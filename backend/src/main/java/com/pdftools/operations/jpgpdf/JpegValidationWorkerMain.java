package com.pdftools.operations.jpgpdf;

import com.pdftools.operations.OperationException;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class JpegValidationWorkerMain {

    private static final int MANIFEST_VERSION = 1;

    private JpegValidationWorkerMain() {
    }

    public static void main(String[] arguments) {
        if (arguments.length != 2) {
            System.err.println("Invalid JPEG validation worker arguments");
            System.exit(3);
        }
        Path errorFile = Path.of(arguments[1]);
        try {
            validateManifest(Path.of(arguments[0]));
        } catch (OperationException exception) {
            writeError(errorFile, exception.getCode(), exception.getMessage());
            System.exit(2);
        } catch (Throwable throwable) {
            throwable.printStackTrace(System.err);
            writeError(
                errorFile,
                "JPEG_VALIDATION_WORKER_FAILED",
                "The isolated JPEG validator failed"
            );
            System.exit(3);
        }
    }

    private static void validateManifest(Path manifest)
            throws IOException {
        JpegInspector inspector = new JpegInspector();
        try (DataInputStream input = new DataInputStream(
                new BufferedInputStream(Files.newInputStream(manifest)))) {
            if (input.readInt() != MANIFEST_VERSION) {
                throw protocolFailure();
            }
            int count = input.readInt();
            if (count < 1 || count > 100) {
                throw protocolFailure();
            }
            for (int index = 0; index < count; index++) {
                Path path = Path.of(input.readUTF());
                int width = input.readInt();
                int height = input.readInt();
                int components = input.readInt();
                inspector.validateDecodableCopy(
                    path,
                    new JpegInspector.JpegInfo(
                        width,
                        height,
                        1,
                        components,
                        -1,
                        false
                    )
                );
            }
            if (input.read() != -1) {
                throw protocolFailure();
            }
        }
    }

    private static OperationException protocolFailure() {
        return new OperationException(
            "JPEG_VALIDATION_PROTOCOL_ERROR",
            "The JPEG validation manifest is invalid"
        );
    }

    private static void writeError(
            Path errorFile,
            String code,
            String message) {
        try {
            Files.writeString(errorFile, code + "\n" + message);
        } catch (IOException exception) {
            exception.printStackTrace(System.err);
        }
    }
}
