package com.pdftools.operations.compress;

import com.pdftools.operations.OperationCancelledException;
import com.pdftools.operations.OperationException;
import com.pdftools.operations.shared.pdf.PdfInputValidator;
import com.pdftools.operations.shared.pdf.PdfPageTreeReader;
import com.pdftools.operations.shared.worker.IsolatedJavaWorker;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBufferedFile;
import org.apache.pdfbox.io.RandomAccessStreamCache;
import org.apache.pdfbox.io.ScratchFile;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.function.IntConsumer;

@Component
public class CompressPdfEngine {

    private static final Logger logger =
        LoggerFactory.getLogger(CompressPdfEngine.class);

    private final CompressPdfProperties properties;

    public CompressPdfEngine(CompressPdfProperties properties) {
        this.properties = properties;
    }

    public Path compress(
            Path source,
            String sourceSha256,
            CompressPdfPlanFactory.CompressPdfPlan plan,
            Path workspace,
            IntConsumer progress,
            Runnable cancellationCheck) {
        DocumentProfile sourceProfile = inspectSource(
            source,
            workspace,
            cancellationCheck
        );
        Path output = workspace.resolve("compressed.pdf");
        Path request = workspace.resolve(".compress-request.bin");
        Path progressFile = workspace.resolve(".compress-progress");
        Path errorFile = workspace.resolve(".compress-error");
        CompressPdfRequest.write(
            request,
            source,
            output,
            workspace,
            sourceSha256,
            plan.mode(),
            properties
        );
        int[] completed = {0};
        int exitCode;
        try {
            progress.accept(5);
            exitCode = IsolatedJavaWorker.run(
                workerSpec(),
                List.of(
                    request.toAbsolutePath().toString(),
                    progressFile.toAbsolutePath().toString(),
                    errorFile.toAbsolutePath().toString()
                ),
                cancellationCheck,
                () -> updateProgress(
                    progressFile,
                    sourceProfile.pages().size(),
                    completed,
                    progress
                )
            );
        } catch (RuntimeException exception) {
            deleteOutput(output, exception);
            throw exception;
        }
        if (exitCode != 0) {
            RuntimeException failure = IsolatedJavaWorker.readFailure(
                exitCode,
                errorFile,
                "COMPRESS_RESOURCE_LIMIT_EXCEEDED",
                "The isolated PDF compression worker exited early",
                logger
            );
            deleteOutput(output, failure);
            throw failure;
        }
        validateOutput(
            output,
            sourceProfile,
            workspace,
            cancellationCheck
        );
        progress.accept(95);
        return output;
    }

    private DocumentProfile inspectSource(
            Path source,
            Path workspace,
            Runnable cancellationCheck) {
        PdfInputValidator.requirePdfHeader(source);
        requireRegularFile(
            source,
            properties.getMaxInputBytes(),
            "COMPRESS_INPUT_LIMIT_EXCEEDED",
            "The PDF exceeds the configured compression input limit"
        );
        try {
            return inspectPdf(
                source,
                workspace.resolve(".compress-inspection"),
                cancellationCheck,
                true
            );
        } catch (InvalidPasswordException exception) {
            throw encryptedPdf(exception);
        } catch (OperationException | OperationCancelledException exception) {
            throw exception;
        } catch (IOException exception) {
            throw invalidPdf(exception);
        }
    }

    private void validateOutput(
            Path output,
            DocumentProfile expected,
            Path workspace,
            Runnable cancellationCheck) {
        try {
            PdfInputValidator.requirePdfHeader(output);
            requireRegularFile(
                output,
                properties.getMaxOutputBytes(),
                "INVALID_COMPRESSED_PDF",
                "The compressed PDF is invalid"
            );
            DocumentProfile actual = inspectPdf(
                output,
                workspace.resolve(".compress-output-inspection"),
                cancellationCheck,
                false
            );
            if (!expected.equals(actual)) {
                throw invalidOutput(null);
            }
        } catch (OperationCancelledException exception) {
            deleteOutput(output, exception);
            throw exception;
        } catch (OperationException exception) {
            deleteOutput(output, exception);
            if (exception.getCode().equals("INVALID_COMPRESSED_PDF")) {
                throw exception;
            }
            throw invalidOutput(exception);
        } catch (IOException exception) {
            OperationException failure = invalidOutput(exception);
            deleteOutput(output, failure);
            throw failure;
        }
    }

    private DocumentProfile inspectPdf(
            Path source,
            Path scratch,
            Runnable cancellationCheck,
            boolean sourceInput) throws IOException {
        Files.createDirectories(scratch);
        RandomAccessStreamCache.StreamCacheCreateFunction scratchCache =
            () -> new ScratchFile(scratch.toFile());
        try (RandomAccessReadBufferedFile randomAccess =
                 new RandomAccessReadBufferedFile(source);
             PDDocument document = Loader.loadPDF(
                 randomAccess,
                 scratchCache
             )) {
            cancellationCheck.run();
            if (document.isEncrypted()) {
                if (sourceInput) {
                    throw encryptedPdf(null);
                }
                throw invalidOutput(null);
            }
            List<PDPage> pages = new PdfPageTreeReader(properties).read(
                document,
                cancellationCheck
            ).pages();
            if (pages.isEmpty()) {
                throw sourceInput
                    ? invalidPdf(null)
                    : invalidOutput(null);
            }
            return new DocumentProfile(
                pages.stream().map(this::profile).toList()
            );
        }
    }

    private PageProfile profile(PDPage page) {
        return new PageProfile(
            rectangle(page.getMediaBox()),
            rectangle(page.getCropBox()),
            page.getRotation(),
            page.getUserUnit()
        );
    }

    private RectangleProfile rectangle(PDRectangle rectangle) {
        return new RectangleProfile(
            rectangle.getLowerLeftX(),
            rectangle.getLowerLeftY(),
            rectangle.getUpperRightX(),
            rectangle.getUpperRightY()
        );
    }

    private void requireRegularFile(
            Path path,
            long maxBytes,
            String code,
            String message) {
        try {
            if (Files.isSymbolicLink(path)
                    || !Files.isRegularFile(
                        path,
                        LinkOption.NOFOLLOW_LINKS)
                    || Files.size(path) < 1
                    || Files.size(path) > maxBytes) {
                throw new OperationException(code, message);
            }
        } catch (OperationException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new OperationException(code, message, exception);
        }
    }

    private void updateProgress(
            Path progressFile,
            int pageCount,
            int[] completed,
            IntConsumer progress) {
        if (!Files.exists(progressFile, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try {
            int current = Integer.parseInt(
                Files.readString(progressFile).trim()
            );
            if (current < 0 || current > pageCount) {
                throw protocolFailure(null);
            }
            if (current > completed[0]) {
                completed[0] = current;
                progress.accept(Math.min(
                    90,
                    5 + (int) Math.floor(85.0 * current / pageCount)
                ));
            }
        } catch (NumberFormatException | IOException exception) {
            throw protocolFailure(exception);
        }
    }

    private IsolatedJavaWorker.Spec workerSpec() {
        return new IsolatedJavaWorker.Spec(
            CompressPdfWorkerMain.class,
            properties.getWorkerHeapBytes(),
            properties.getWorkerTimeout(),
            "COMPRESS_WORKER_START_FAILED",
            "The isolated PDF compression worker could not be started",
            "COMPRESS_TIMEOUT",
            "PDF compression exceeded its time limit"
        );
    }

    private void deleteOutput(Path output, RuntimeException failure) {
        try {
            Files.deleteIfExists(output);
        } catch (IOException exception) {
            failure.addSuppressed(exception);
            logger.error(
                "Could not remove partial compressed PDF {}",
                output,
                exception
            );
        }
    }

    private OperationException protocolFailure(Throwable cause) {
        return new OperationException(
            "COMPRESS_WORKER_PROTOCOL_ERROR",
            "The PDF compression worker returned invalid state",
            cause
        );
    }

    private OperationException encryptedPdf(Throwable cause) {
        return new OperationException(
            "ENCRYPTED_PDF_NOT_SUPPORTED",
            "Compress PDF requires an unencrypted PDF",
            cause
        );
    }

    private OperationException invalidPdf(Throwable cause) {
        return new OperationException(
            "INVALID_PDF",
            "The input is not a readable PDF",
            cause
        );
    }

    private OperationException invalidOutput(Throwable cause) {
        return new OperationException(
            "INVALID_COMPRESSED_PDF",
            "The compressed PDF is invalid",
            cause
        );
    }

    private record DocumentProfile(List<PageProfile> pages) {
    }

    private record PageProfile(
        RectangleProfile mediaBox,
        RectangleProfile cropBox,
        int rotation,
        float userUnit
    ) {
    }

    private record RectangleProfile(
        float lowerLeftX,
        float lowerLeftY,
        float upperRightX,
        float upperRightY
    ) {
    }
}
