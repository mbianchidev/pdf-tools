package com.pdftools.operations.pdfjpg;

import com.pdftools.operations.OperationCancelledException;
import com.pdftools.operations.OperationException;
import com.pdftools.operations.shared.pdf.PdfInputValidator;
import com.pdftools.operations.shared.pdf.PdfPageTreeReader;
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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;

@Component
public class PdfToJpgEngine {

    private static final Logger logger =
        LoggerFactory.getLogger(PdfToJpgEngine.class);
    private static final long MIN_WORKER_HEAP_BYTES = 32L * 1024L * 1024L;
    private static final Duration MAX_WORKER_TIMEOUT = Duration.ofHours(1);

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
        validateWorkerLimits();
        PdfToJpgPlanFactory.PdfToJpgPlan plan = inspect(
            source,
            options,
            workspace,
            cancellationCheck
        );
        Path progressFile = workspace.resolve(".jpg-progress");
        Path errorFile = workspace.resolve(".jpg-error");
        Process worker = startWorker(
            source,
            workspace,
            progressFile,
            errorFile,
            plan
        );
        try {
            progress.accept(5);
            waitForWorker(
                worker,
                progressFile,
                plan.pages().size(),
                progress,
                cancellationCheck
            );
        } catch (RuntimeException exception) {
            terminate(worker);
            cleanupExpectedOutputs(workspace, plan.pages(), exception);
            throw exception;
        }
        int exitCode = worker.exitValue();
        if (exitCode != 0) {
            RuntimeException failure = workerFailure(exitCode, errorFile);
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

    private Process startWorker(
            Path source,
            Path workspace,
            Path progressFile,
            Path errorFile,
            PdfToJpgPlanFactory.PdfToJpgPlan plan) {
        List<String> command = workerCommand(
            source,
            workspace,
            progressFile,
            errorFile,
            plan
        );
        ProcessBuilder builder = new ProcessBuilder(command)
            .redirectOutput(ProcessBuilder.Redirect.INHERIT)
            .redirectError(ProcessBuilder.Redirect.INHERIT);
        builder.environment().remove("JAVA_TOOL_OPTIONS");
        builder.environment().remove("_JAVA_OPTIONS");
        builder.environment().remove("JDK_JAVA_OPTIONS");
        builder.environment().remove("CLASSPATH");
        try {
            return builder.start();
        } catch (IOException exception) {
            throw new OperationException(
                "JPG_WORKER_START_FAILED",
                "The isolated PDF renderer could not be started",
                exception
            );
        }
    }

    private List<String> workerCommand(
            Path source,
            Path workspace,
            Path progressFile,
            Path errorFile,
            PdfToJpgPlanFactory.PdfToJpgPlan plan) {
        String classpath = System.getProperty(
            "surefire.test.class.path",
            System.getProperty("java.class.path")
        );
        List<String> command = new ArrayList<>();
        command.add(Path.of(
            System.getProperty("java.home"),
            "bin",
            "java"
        ).toString());
        command.add("-Xmx" + properties.getMaxWorkerHeapBytes());
        command.add("-XX:+ExitOnOutOfMemoryError");
        command.add("-Djava.awt.headless=true");
        command.add("-cp");
        command.add(classpath);
        if (isBootJarClasspath(classpath)) {
            command.add(
                "-Dloader.main="
                    + PdfToJpgWorkerMain.class.getName()
            );
            command.add(
                "org.springframework.boot.loader.launch.PropertiesLauncher"
            );
        } else {
            command.add(PdfToJpgWorkerMain.class.getName());
        }
        command.add(source.toAbsolutePath().toString());
        command.add(workspace.toAbsolutePath().toString());
        command.add(progressFile.toAbsolutePath().toString());
        command.add(errorFile.toAbsolutePath().toString());
        command.add(plan.pages().stream()
            .map(String::valueOf)
            .reduce((left, right) -> left + "," + right)
            .orElseThrow());
        command.add(Integer.toString(plan.dpi()));
        command.add(Integer.toString(plan.quality()));
        command.add(Integer.toString(properties.getMaxDocumentPages()));
        command.add(Integer.toString(properties.getMaxSelectedPages()));
        command.add(Integer.toString(properties.getMinDpi()));
        command.add(Integer.toString(properties.getMaxDpi()));
        command.add(Long.toString(properties.getMaxPixelsPerPage()));
        command.add(Integer.toString(properties.getMaxImageDimension()));
        command.add(Long.toString(properties.getMaxImageBytes()));
        command.add(Long.toString(properties.getMaxTotalImageBytes()));
        command.add(Integer.toString(properties.maxPageTreeNodes()));
        command.add(Integer.toString(properties.maxPageTreeDepth()));
        command.add(Integer.toString(
            properties.maxContentStreamsPerPage()
        ));
        return command;
    }

    private boolean isBootJarClasspath(String classpath) {
        return !classpath.contains(File.pathSeparator)
            && classpath.toLowerCase(Locale.ROOT).endsWith(".jar");
    }

    private void waitForWorker(
            Process worker,
            Path progressFile,
            int pageCount,
            IntConsumer progress,
            Runnable cancellationCheck) {
        long timeoutNanos = properties.getWorkerTimeout().toNanos();
        long started = System.nanoTime();
        int completed = 0;
        try {
            while (!worker.waitFor(100, TimeUnit.MILLISECONDS)) {
                cancellationCheck.run();
                if (System.nanoTime() - started >= timeoutNanos) {
                    throw new OperationException(
                        "JPG_RENDER_TIMEOUT",
                        "PDF rendering exceeded the configured time limit"
                    );
                }
                int nextCompleted = readProgress(
                    progressFile,
                    pageCount
                );
                if (nextCompleted > completed) {
                    completed = nextCompleted;
                    int nextProgress = 5 + (int) Math.floor(
                        85.0 * completed / pageCount
                    );
                    progress.accept(Math.min(nextProgress, 90));
                }
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new OperationCancelledException();
        }
        cancellationCheck.run();
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

    private RuntimeException workerFailure(
            int exitCode,
            Path errorFile) {
        if (exitCode == 2
                && Files.isRegularFile(
                    errorFile,
                    LinkOption.NOFOLLOW_LINKS)) {
            try {
                List<String> lines = Files.readAllLines(errorFile);
                if (lines.size() >= 2
                        && lines.getFirst().matches("[A-Z0-9_]{1,96}")) {
                    return new OperationException(
                        lines.getFirst(),
                        lines.get(1)
                    );
                }
            } catch (IOException exception) {
                logger.warn(
                    "Could not read isolated renderer error {}",
                    errorFile,
                    exception
                );
            }
        }
        return new OperationException(
            "JPG_RENDER_RESOURCE_LIMIT_EXCEEDED",
            "The isolated PDF renderer exited before completing"
        );
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

    private void terminate(Process worker) {
        if (!worker.isAlive()) {
            return;
        }
        worker.destroy();
        try {
            if (!worker.waitFor(2, TimeUnit.SECONDS)) {
                worker.destroyForcibly();
                worker.waitFor(2, TimeUnit.SECONDS);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            worker.destroyForcibly();
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

    private void validateWorkerLimits() {
        long heapBytes = properties.getMaxWorkerHeapBytes();
        Duration timeout = properties.getWorkerTimeout();
        if (heapBytes < MIN_WORKER_HEAP_BYTES
                || timeout == null
                || timeout.isZero()
                || timeout.isNegative()
                || timeout.compareTo(MAX_WORKER_TIMEOUT) > 0) {
            throw new IllegalStateException(
                "PDF-to-JPG worker limits are invalid"
            );
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
