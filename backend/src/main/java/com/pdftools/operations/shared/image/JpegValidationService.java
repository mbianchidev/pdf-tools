package com.pdftools.operations.shared.image;

import com.pdftools.operations.OperationCancelledException;
import com.pdftools.operations.OperationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
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

@Component
public class JpegValidationService {

    private static final Logger logger =
        LoggerFactory.getLogger(JpegValidationService.class);
    private static final int MANIFEST_VERSION = 1;
    private static final long MIN_WORKER_HEAP_BYTES = 32L * 1024L * 1024L;
    private static final Duration MAX_WORKER_TIMEOUT = Duration.ofHours(1);

    private final JpegValidationProperties properties;
    private final JpegInspector inspector = new JpegInspector();

    public JpegValidationService(
            JpegValidationProperties properties) {
        this.properties = properties;
    }

    public ValidationArtifacts validate(
            List<JpegValidationInput> inputs,
            Path workspace,
            Runnable cancellationCheck) {
        validateLimits();
        Path manifest = workspace.resolve(".jpeg-validation.bin");
        Path errorFile = workspace.resolve(".jpeg-validation-error");
        List<Path> copies = new ArrayList<>(inputs.size());
        Process worker = null;
        RuntimeException failure = null;
        boolean success = false;
        try {
            for (int index = 0; index < inputs.size(); index++) {
                cancellationCheck.run();
                Path copy = workspace.resolve(String.format(
                    Locale.ROOT,
                    ".jpeg-validation-%04d.jpg",
                    index
                ));
                inspector.writeValidationCopy(
                    inputs.get(index).source(),
                    copy,
                    inputs.get(index).info(),
                    cancellationCheck
                );
                copies.add(copy);
            }
            writeManifest(manifest, copies, inputs);
            worker = startWorker(manifest, errorFile);
            waitForWorker(worker, cancellationCheck);
            if (worker.exitValue() != 0) {
                throw workerFailure(worker.exitValue(), errorFile);
            }
            success = true;
            return new ValidationArtifacts(copies);
        } catch (RuntimeException exception) {
            failure = exception;
            if (worker != null) {
                terminate(worker);
            }
            throw exception;
        } finally {
            cleanup(
                success ? List.of() : copies,
                manifest,
                errorFile,
                failure
            );
        }
    }

    private void writeManifest(
            Path manifest,
            List<Path> copies,
            List<JpegValidationInput> inputs) {
        try (DataOutputStream output = new DataOutputStream(
                new BufferedOutputStream(Files.newOutputStream(manifest)))) {
            output.writeInt(MANIFEST_VERSION);
            output.writeInt(inputs.size());
            for (int index = 0; index < inputs.size(); index++) {
                JpegInspector.JpegInfo info = inputs.get(index).info();
                output.writeUTF(copies.get(index).toAbsolutePath().toString());
                output.writeInt(info.width());
                output.writeInt(info.height());
                output.writeInt(info.components());
            }
        } catch (IOException exception) {
            throw new OperationException(
                "JPEG_VALIDATION_PROTOCOL_ERROR",
                "The JPEG validation manifest could not be written",
                exception
            );
        }
    }

    private Process startWorker(Path manifest, Path errorFile) {
        ProcessBuilder builder = new ProcessBuilder(workerCommand(
            manifest,
            errorFile
        ))
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
                "JPEG_VALIDATION_WORKER_START_FAILED",
                "The isolated JPEG validator could not be started",
                exception
            );
        }
    }

    private List<String> workerCommand(Path manifest, Path errorFile) {
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
        command.add(
            "-Xmx" + properties.getWorkerHeapBytes()
        );
        command.add("-XX:+ExitOnOutOfMemoryError");
        command.add("-Djava.awt.headless=true");
        command.add("-cp");
        command.add(classpath);
        if (!classpath.contains(File.pathSeparator)
                && classpath.toLowerCase(Locale.ROOT).endsWith(".jar")) {
            command.add(
                "-Dloader.main="
                    + JpegValidationWorkerMain.class.getName()
            );
            command.add(
                "org.springframework.boot.loader.launch.PropertiesLauncher"
            );
        } else {
            command.add(JpegValidationWorkerMain.class.getName());
        }
        command.add(manifest.toAbsolutePath().toString());
        command.add(errorFile.toAbsolutePath().toString());
        return List.copyOf(command);
    }

    private void waitForWorker(
            Process worker,
            Runnable cancellationCheck) {
        long timeoutNanos =
            properties.getWorkerTimeout().toNanos();
        long started = System.nanoTime();
        try {
            while (!worker.waitFor(100, TimeUnit.MILLISECONDS)) {
                cancellationCheck.run();
                if (System.nanoTime() - started >= timeoutNanos) {
                    throw new OperationException(
                        "JPEG_VALIDATION_TIMEOUT",
                        "JPEG validation exceeded the configured time limit"
                    );
                }
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new OperationCancelledException();
        }
        cancellationCheck.run();
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
                    "Could not read JPEG validation error {}",
                    errorFile,
                    exception
                );
            }
        }
        return new OperationException(
            "JPEG_VALIDATION_RESOURCE_LIMIT_EXCEEDED",
            "The isolated JPEG validator exited before completing"
        );
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

    private void cleanup(
            List<Path> copies,
            Path manifest,
            Path errorFile,
            RuntimeException failure) {
        List<Path> paths = new ArrayList<>(copies);
        paths.add(manifest);
        paths.add(errorFile);
        cleanupPaths(paths, failure);
    }

    private void cleanupPaths(
            List<Path> paths,
            RuntimeException failure) {
        for (Path path : paths) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException exception) {
                OperationException cleanupFailure = new OperationException(
                    "JPG_PDF_CLEANUP_FAILED",
                    "JPEG validation scratch could not be removed",
                    exception
                );
                if (failure != null) {
                    failure.addSuppressed(cleanupFailure);
                }
                logger.error(
                    "Could not remove JPEG validation scratch {}",
                    path,
                    cleanupFailure
                );
            }
        }
    }

    public final class ValidationArtifacts implements AutoCloseable {

        private final List<Path> paths;
        private boolean closed;

        private ValidationArtifacts(List<Path> paths) {
            this.paths = List.copyOf(paths);
        }

        public List<Path> paths() {
            return paths;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            cleanupPaths(paths, null);
        }
    }

    private void validateLimits() {
        long heap = properties.getWorkerHeapBytes();
        Duration timeout = properties.getWorkerTimeout();
        if (heap < MIN_WORKER_HEAP_BYTES
                || timeout == null
                || timeout.isZero()
                || timeout.isNegative()
                || timeout.compareTo(MAX_WORKER_TIMEOUT) > 0) {
            throw new IllegalStateException(
                "JPEG validation worker limits are invalid"
            );
        }
    }
}
