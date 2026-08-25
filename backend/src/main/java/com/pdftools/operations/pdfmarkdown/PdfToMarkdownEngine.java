package com.pdftools.operations.pdfmarkdown;

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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntConsumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Component
public class PdfToMarkdownEngine {

    private static final Logger logger =
        LoggerFactory.getLogger(PdfToMarkdownEngine.class);

    private final PdfToMarkdownProperties properties;

    public PdfToMarkdownEngine(PdfToMarkdownProperties properties) {
        this.properties = properties;
    }

    public Path convert(
            Path source,
            PdfToMarkdownPlanFactory.PdfToMarkdownPlan plan,
            Path workspace,
            IntConsumer progress,
            Runnable cancellationCheck) {
        int pageCount = inspect(source, workspace, cancellationCheck);
        Path output = workspace.resolve("pdf-to-markdown.zip");
        Path request = workspace.resolve(".pdf-markdown-request.bin");
        Path progressFile = workspace.resolve(".pdf-markdown-progress");
        Path errorFile = workspace.resolve(".pdf-markdown-error");
        PdfToMarkdownRequest.write(
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
                "PDF_MARKDOWN_RESOURCE_LIMIT_EXCEEDED",
                "The isolated PDF-to-Markdown worker exited early",
                logger
            );
            deleteOutput(output, failure);
            throw failure;
        }
        validateBundle(output);
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
                    "PDF_MARKDOWN_INPUT_LIMIT_EXCEEDED",
                    "The PDF exceeds the configured input limit"
                );
            }
        } catch (OperationException exception) {
            throw exception;
        } catch (IOException exception) {
            throw invalidPdf(exception);
        }
        Path scratch = workspace.resolve(".pdf-markdown-inspection");
        try {
            Files.createDirectories(scratch);
        } catch (IOException exception) {
            throw new OperationException(
                "PDF_MARKDOWN_SCRATCH_FAILED",
                "PDF-to-Markdown inspection scratch could not be created",
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
            PdfToMarkdownWorkerMain.class,
            properties.getWorkerHeapBytes(),
            properties.getWorkerTimeout(),
            "PDF_MARKDOWN_WORKER_START_FAILED",
            "The isolated PDF-to-Markdown worker could not be started",
            "PDF_MARKDOWN_TIMEOUT",
            "PDF-to-Markdown conversion exceeded its time limit"
        );
    }

    private void validateBundle(Path output) {
        try {
            if (Files.isSymbolicLink(output)
                    || !Files.isRegularFile(
                        output,
                        LinkOption.NOFOLLOW_LINKS)
                    || Files.size(output) < 1
                    || Files.size(output) > properties.getMaxOutputBytes()) {
                throw invalidOutput(null);
            }
            boolean markdown = false;
            int entries = 0;
            long expanded = 0;
            long maxExpanded = Math.multiplyExact(
                properties.getMaxOutputBytes(),
                4L
            );
            Set<String> names = new HashSet<>();
            byte[] buffer = new byte[8192];
            try (InputStream file = Files.newInputStream(output);
                 ZipInputStream archive = new ZipInputStream(file)) {
                ZipEntry entry;
                while ((entry = archive.getNextEntry()) != null) {
                    entries++;
                    String name = entry.getName();
                    if (entries > properties.getMaxImages() + 1
                            || !names.add(name)
                            || (!name.equals("document.md")
                                && !name.matches(
                                    "images/page-\\d{3,}-image-\\d{3,}\\.png"))) {
                        throw invalidOutput(null);
                    }
                    markdown |= name.equals("document.md");
                    long entryBytes = 0;
                    int read;
                    while ((read = archive.read(buffer)) != -1) {
                        entryBytes = Math.addExact(entryBytes, read);
                        expanded = Math.addExact(expanded, read);
                        if (expanded > maxExpanded
                                || (name.equals("document.md")
                                    && entryBytes
                                        > properties
                                            .getMaxMarkdownCharacters()
                                            * 4L)) {
                            throw invalidOutput(null);
                        }
                    }
                    if (name.equals("document.md") && entryBytes < 1) {
                        throw invalidOutput(null);
                    }
                }
            }
            if (!markdown) {
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
                "Could not remove partial PDF-to-Markdown output {}",
                output,
                exception
            );
        }
    }

    private OperationException protocolFailure(Throwable cause) {
        return new OperationException(
            "PDF_MARKDOWN_WORKER_PROTOCOL_ERROR",
            "The PDF-to-Markdown worker returned invalid state",
            cause
        );
    }

    private OperationException invalidOutput(Throwable cause) {
        return new OperationException(
            "INVALID_PDF_MARKDOWN_OUTPUT",
            "The generated Markdown bundle is invalid",
            cause
        );
    }

    private OperationException encryptedPdf(Throwable cause) {
        return new OperationException(
            "ENCRYPTED_PDF_NOT_SUPPORTED",
            "PDF to Markdown requires an unencrypted PDF",
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
