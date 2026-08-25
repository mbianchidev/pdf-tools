package com.pdftools.operations.pdfjpg;

import com.pdftools.operations.OperationException;
import com.pdftools.operations.shared.worker.IsolatedJavaWorker;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

public final class PdfToJpgWorkerMain {

    private static final int ARGUMENT_COUNT = 18;

    private PdfToJpgWorkerMain() {
    }

    public static void main(String[] arguments) {
        if (arguments.length != ARGUMENT_COUNT) {
            System.err.println("Invalid PDF-to-JPG worker arguments");
            System.exit(3);
        }
        Path errorFile = Path.of(arguments[3]);
        try {
            PdfToJpgProperties properties = properties(arguments);
            List<Integer> pages = Arrays.stream(arguments[4].split(","))
                .map(Integer::parseInt)
                .toList();
            new PdfToJpgWorker(properties).render(
                Path.of(arguments[0]),
                Path.of(arguments[1]),
                Path.of(arguments[2]),
                pages,
                Integer.parseInt(arguments[5]),
                Integer.parseInt(arguments[6])
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
                "JPG_RENDER_WORKER_FAILED",
                "The isolated PDF renderer failed"
            );
            System.exit(3);
        }
    }

    private static PdfToJpgProperties properties(String[] arguments) {
        PdfToJpgProperties properties = new PdfToJpgProperties();
        properties.setMaxDocumentPages(Integer.parseInt(arguments[7]));
        properties.setMaxSelectedPages(Integer.parseInt(arguments[8]));
        properties.setMinDpi(Integer.parseInt(arguments[9]));
        properties.setMaxDpi(Integer.parseInt(arguments[10]));
        properties.setMaxPixelsPerPage(Long.parseLong(arguments[11]));
        properties.setMaxImageDimension(Integer.parseInt(arguments[12]));
        properties.setMaxImageBytes(Long.parseLong(arguments[13]));
        properties.setMaxTotalImageBytes(Long.parseLong(arguments[14]));
        properties.setMaxPageTreeNodes(Integer.parseInt(arguments[15]));
        properties.setMaxPageTreeDepth(Integer.parseInt(arguments[16]));
        properties.setMaxContentStreamsPerPage(
            Integer.parseInt(arguments[17])
        );
        return properties;
    }

}
