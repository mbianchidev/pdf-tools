package com.pdftools.operations.pdfexcel;

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
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Component
public class PdfToExcelEngine {

    private static final Logger logger =
        LoggerFactory.getLogger(PdfToExcelEngine.class);

    private final PdfToExcelProperties properties;

    public PdfToExcelEngine(PdfToExcelProperties properties) {
        this.properties = properties;
    }

    public Path convert(
            Path source,
            PdfToExcelPlanFactory.PdfToExcelPlan plan,
            Path workspace,
            IntConsumer progress,
            Runnable cancellationCheck) {
        int pageCount = inspect(source, workspace, cancellationCheck);
        Path output = workspace.resolve("pdf-to-excel.xlsx");
        Path request = workspace.resolve(".pdf-excel-request.bin");
        Path progressFile = workspace.resolve(".pdf-excel-progress");
        Path errorFile = workspace.resolve(".pdf-excel-error");
        PdfToExcelRequest.write(
            request,
            source,
            output,
            workspace,
            plan,
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
                    pageCount,
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
                "PDF_EXCEL_RESOURCE_LIMIT_EXCEEDED",
                "The isolated PDF-to-Excel worker exited early",
                logger
            );
            deleteOutput(output, failure);
            throw failure;
        }
        validateXlsx(output);
        progress.accept(95);
        return output;
    }

    private int inspect(
            Path source,
            Path workspace,
            Runnable cancellationCheck) {
        PdfInputValidator.requirePdfHeader(source);
        try {
            if (Files.isSymbolicLink(source)
                    || !Files.isRegularFile(
                        source,
                        LinkOption.NOFOLLOW_LINKS)
                    || Files.size(source) < 1
                    || Files.size(source) > properties.getMaxInputBytes()) {
                throw new OperationException(
                    "PDF_EXCEL_INPUT_LIMIT_EXCEEDED",
                    "The PDF exceeds the configured input limit"
                );
            }
        } catch (OperationException exception) {
            throw exception;
        } catch (IOException exception) {
            throw invalidPdf(exception);
        }
        Path scratch = workspace.resolve(".pdf-excel-inspection");
        try {
            Files.createDirectories(scratch);
        } catch (IOException exception) {
            throw new OperationException(
                "PDF_EXCEL_SCRATCH_FAILED",
                "PDF-to-Excel inspection scratch could not be created",
                exception
            );
        }
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
                throw encryptedPdf(null);
            }
            return new PdfPageTreeReader(properties).read(
                document,
                cancellationCheck
            ).pages().size();
        } catch (InvalidPasswordException exception) {
            throw encryptedPdf(exception);
        } catch (OperationException | OperationCancelledException exception) {
            throw exception;
        } catch (IOException exception) {
            throw invalidPdf(exception);
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
            PdfToExcelWorkerMain.class,
            properties.getWorkerHeapBytes(),
            properties.getWorkerTimeout(),
            "PDF_EXCEL_WORKER_START_FAILED",
            "The isolated PDF-to-Excel worker could not be started",
            "PDF_EXCEL_TIMEOUT",
            "PDF-to-Excel conversion exceeded its time limit"
        );
    }

    private void validateXlsx(Path output) {
        try {
            if (Files.isSymbolicLink(output)
                    || !Files.isRegularFile(
                        output,
                        LinkOption.NOFOLLOW_LINKS)
                    || Files.size(output) < 1
                    || Files.size(output) > properties.getMaxOutputBytes()) {
                throw invalidOutput(null);
            }
            boolean contentTypes = false;
            boolean workbook = false;
            int worksheets = 0;
            int entries = 0;
            long expanded = 0;
            byte[] buffer = new byte[8192];
            try (InputStream file = Files.newInputStream(output);
                 ZipInputStream archive = new ZipInputStream(file)) {
                ZipEntry entry;
                while ((entry = archive.getNextEntry()) != null) {
                    entries++;
                    if (entries > 5000) {
                        throw invalidOutput(null);
                    }
                    String name = entry.getName();
                    contentTypes |= name.equals("[Content_Types].xml");
                    workbook |= name.equals("xl/workbook.xml");
                    if (name.matches(
                            "xl/worksheets/sheet\\d+\\.xml")) {
                        worksheets++;
                    }
                    int read;
                    while ((read = archive.read(buffer)) != -1) {
                        expanded = Math.addExact(expanded, read);
                        if (expanded > properties.getMaxOutputBytes() * 4) {
                            throw invalidOutput(null);
                        }
                    }
                }
            }
            if (!contentTypes
                    || !workbook
                    || worksheets < 1
                    || worksheets > properties.getMaxSheets()) {
                throw invalidOutput(null);
            }
        } catch (OperationException exception) {
            deleteOutput(output, exception);
            throw exception;
        } catch (IOException | ArithmeticException exception) {
            OperationException failure = invalidOutput(exception);
            deleteOutput(output, failure);
            throw failure;
        }
    }

    private void deleteOutput(Path output, RuntimeException failure) {
        try {
            Files.deleteIfExists(output);
        } catch (IOException exception) {
            failure.addSuppressed(exception);
            logger.error(
                "Could not remove partial PDF-to-Excel output {}",
                output,
                exception
            );
        }
    }

    private OperationException protocolFailure(Throwable cause) {
        return new OperationException(
            "PDF_EXCEL_WORKER_PROTOCOL_ERROR",
            "The PDF-to-Excel worker returned invalid state",
            cause
        );
    }

    private OperationException invalidOutput(Throwable cause) {
        return new OperationException(
            "INVALID_PDF_EXCEL_OUTPUT",
            "The generated Excel workbook is invalid",
            cause
        );
    }

    private OperationException encryptedPdf(Throwable cause) {
        return new OperationException(
            "ENCRYPTED_PDF_NOT_SUPPORTED",
            "PDF to Excel requires an unencrypted PDF",
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
}
