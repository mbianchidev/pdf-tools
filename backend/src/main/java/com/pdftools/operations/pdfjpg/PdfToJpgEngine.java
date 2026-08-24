package com.pdftools.operations.pdfjpg;

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
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.IntConsumer;

@Component
public class PdfToJpgEngine {

    private static final Logger logger =
        LoggerFactory.getLogger(PdfToJpgEngine.class);
    private final PdfToJpgPlanFactory planFactory;
    private final PdfToJpgProperties properties;
    private final PdfPageTreeReader pageTreeReader;

    public PdfToJpgEngine(
            PdfToJpgPlanFactory planFactory,
            PdfToJpgProperties properties) {
        this.planFactory = planFactory;
        this.properties = properties;
        this.pageTreeReader = new PdfPageTreeReader(properties);
    }

    public PdfToJpgResult render(
            Path source,
            JsonNode options,
            Path workspace,
            IntConsumer progress,
            Runnable cancellationCheck) {
        IsolatedJavaWorker.Spec workerSpec = workerSpec();
        PdfToJpgPlanFactory.PdfToJpgPlan plan = inspect(
            source,
            options,
            workspace,
            cancellationCheck
        );
        Path progressFile = workspace.resolve(".jpg-progress");
        Path errorFile = workspace.resolve(".jpg-error");
        int[] completed = {0};
        int exitCode;
        try {
            progress.accept(5);
            exitCode = IsolatedJavaWorker.run(
                workerSpec,
                workerArguments(
                    source,
                    workspace,
                    progressFile,
                    errorFile,
                    plan
                ),
                cancellationCheck,
                () -> updateProgress(
                    progressFile,
                    plan.pages().size(),
                    completed,
                    progress
                )
            );
        } catch (RuntimeException exception) {
            cleanupExpectedOutputs(workspace, plan.pages(), exception);
            throw exception;
        }
        if (exitCode != 0) {
            RuntimeException failure = IsolatedJavaWorker.readFailure(
                exitCode,
                errorFile,
                "JPG_RENDER_RESOURCE_LIMIT_EXCEEDED",
                "The isolated PDF renderer exited before completing",
                logger
            );
            cleanupExpectedOutputs(workspace, plan.pages(), failure);
            throw failure;
        }
        PdfToJpgResult result = collectOutputs(
            workspace,
            plan.pages()
        );
        progress.accept(90);
        return result;
    }

    private PdfToJpgPlanFactory.PdfToJpgPlan inspect(
            Path source,
            JsonNode options,
            Path workspace,
            Runnable cancellationCheck) {
        PdfInputValidator.requirePdfHeader(source);
        Path scratchDirectory = workspace.resolve(".inspection-scratch");
        try {
            Files.createDirectories(scratchDirectory);
        } catch (IOException exception) {
            throw new OperationException(
                "JPG_SCRATCH_FAILED",
                "PDF-to-JPG inspection scratch could not be created",
                exception
            );
        }
        RandomAccessStreamCache.StreamCacheCreateFunction scratchCache =
            () -> new ScratchFile(scratchDirectory.toFile());
        try (RandomAccessReadBufferedFile randomAccess =
                 new RandomAccessReadBufferedFile(source);
             PDDocument document = Loader.loadPDF(
                 randomAccess,
                 scratchCache
             )) {
            cancellationCheck.run();
            if (document.isEncrypted()) {
                throw encryptedPdf();
            }
            PdfPageTreeReader.Result pageTree = pageTreeReader.read(
                document,
                cancellationCheck
            );
            return planFactory.create(options, pageTree.pages().size());
        } catch (InvalidPasswordException exception) {
            throw encryptedPdf();
        } catch (OperationException | OperationCancelledException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new OperationException(
                "INVALID_PDF",
                "The input is not a readable PDF",
                exception
            );
        }
    }

    private List<String> workerArguments(
            Path source,
            Path workspace,
            Path progressFile,
            Path errorFile,
            PdfToJpgPlanFactory.PdfToJpgPlan plan) {
        List<String> arguments = new ArrayList<>();
        arguments.add(source.toAbsolutePath().toString());
        arguments.add(workspace.toAbsolutePath().toString());
        arguments.add(progressFile.toAbsolutePath().toString());
        arguments.add(errorFile.toAbsolutePath().toString());
        arguments.add(plan.pages().stream()
            .map(String::valueOf)
            .reduce((left, right) -> left + "," + right)
            .orElseThrow());
        arguments.add(Integer.toString(plan.dpi()));
        arguments.add(Integer.toString(plan.quality()));
        arguments.add(Integer.toString(properties.getMaxDocumentPages()));
        arguments.add(Integer.toString(properties.getMaxSelectedPages()));
        arguments.add(Integer.toString(properties.getMinDpi()));
        arguments.add(Integer.toString(properties.getMaxDpi()));
        arguments.add(Long.toString(properties.getMaxPixelsPerPage()));
        arguments.add(Integer.toString(properties.getMaxImageDimension()));
        arguments.add(Long.toString(properties.getMaxImageBytes()));
        arguments.add(Long.toString(properties.getMaxTotalImageBytes()));
        arguments.add(Integer.toString(properties.maxPageTreeNodes()));
        arguments.add(Integer.toString(properties.maxPageTreeDepth()));
        arguments.add(Integer.toString(
            properties.maxContentStreamsPerPage()
        ));
        return List.copyOf(arguments);
    }

    private IsolatedJavaWorker.Spec workerSpec() {
        return new IsolatedJavaWorker.Spec(
            PdfToJpgWorkerMain.class,
            properties.getMaxWorkerHeapBytes(),
            properties.getWorkerTimeout(),
            "JPG_WORKER_START_FAILED",
            "The isolated PDF renderer could not be started",
            "JPG_RENDER_TIMEOUT",
            "PDF rendering exceeded the configured time limit"
        );
    }

    private void updateProgress(
            Path progressFile,
            int pageCount,
            int[] completed,
            IntConsumer progress) {
        int nextCompleted = readProgress(progressFile, pageCount);
        if (nextCompleted > completed[0]) {
            completed[0] = nextCompleted;
            int nextProgress = 5 + (int) Math.floor(
                85.0 * completed[0] / pageCount
            );
            progress.accept(Math.min(nextProgress, 90));
        }
    }

    private int readProgress(Path progressFile, int pageCount) {
        if (!Files.exists(progressFile, LinkOption.NOFOLLOW_LINKS)) {
            return 0;
        }
        try {
            int completed = Integer.parseInt(
                Files.readString(progressFile).trim()
            );
            if (completed < 0 || completed > pageCount) {
                throw workerProtocolFailure();
            }
            return completed;
        } catch (NumberFormatException | IOException exception) {
            throw workerProtocolFailure();
        }
    }

    private PdfToJpgResult collectOutputs(
            Path workspace,
            List<Integer> pages) {
        List<PdfToJpgResult.Part> parts = new ArrayList<>(pages.size());
        long totalBytes = 0;
        try {
            for (int pageNumber : pages) {
                Path output = outputPath(workspace, pageNumber);
                if (Files.isSymbolicLink(output)
                        || !Files.isRegularFile(
                            output,
                            LinkOption.NOFOLLOW_LINKS)) {
                    throw workerProtocolFailure();
                }
                long imageBytes = Files.size(output);
                if (imageBytes < 1
                        || imageBytes > properties.getMaxImageBytes()) {
                    throw workerProtocolFailure();
                }
                totalBytes = Math.addExact(totalBytes, imageBytes);
                if (totalBytes > properties.getMaxTotalImageBytes()) {
                    throw workerProtocolFailure();
                }
                parts.add(new PdfToJpgResult.Part(
                    output,
                    pageNumber
                ));
            }
            return new PdfToJpgResult(parts);
        } catch (IOException | ArithmeticException exception) {
            OperationException failure = workerProtocolFailure();
            cleanupExpectedOutputs(workspace, pages, failure);
            throw failure;
        } catch (RuntimeException failure) {
            cleanupExpectedOutputs(workspace, pages, failure);
            throw failure;
        }
    }

    private void cleanupExpectedOutputs(
            Path workspace,
            List<Integer> pages,
            RuntimeException failure) {
        for (int pageNumber : pages) {
            Path output = outputPath(workspace, pageNumber);
            try {
                Files.deleteIfExists(output);
            } catch (IOException exception) {
                OperationException cleanupFailure = new OperationException(
                    "JPG_CLEANUP_FAILED",
                    "Partial JPG output could not be removed",
                    exception
                );
                failure.addSuppressed(cleanupFailure);
                logger.error(
                    "Could not remove partial JPG output {}",
                    output,
                    cleanupFailure
                );
            }
        }
    }

    private Path outputPath(Path workspace, int pageNumber) {
        return workspace.resolve(String.format(
            Locale.ROOT,
            "page-%04d.jpg",
            pageNumber
        ));
    }

    private OperationException encryptedPdf() {
        return new OperationException(
            "ENCRYPTED_PDF",
            "Unlock the PDF before converting it to JPG"
        );
    }

    private OperationException workerProtocolFailure() {
        return new OperationException(
            "JPG_WORKER_PROTOCOL_ERROR",
            "The isolated PDF renderer returned an invalid result"
        );
    }
}
