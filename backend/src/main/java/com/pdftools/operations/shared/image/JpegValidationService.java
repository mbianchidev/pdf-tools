package com.pdftools.operations.shared.image;

import com.pdftools.operations.OperationException;
import com.pdftools.operations.shared.worker.IsolatedJavaWorker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class JpegValidationService {

    private static final Logger logger =
        LoggerFactory.getLogger(JpegValidationService.class);
    private static final int MANIFEST_VERSION = 1;
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
        IsolatedJavaWorker.Spec workerSpec = workerSpec();
        Path manifest = workspace.resolve(".jpeg-validation.bin");
        Path errorFile = workspace.resolve(".jpeg-validation-error");
        List<Path> copies = new ArrayList<>(inputs.size());
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
            int exitCode = IsolatedJavaWorker.run(
                workerSpec,
                List.of(
                    manifest.toAbsolutePath().toString(),
                    errorFile.toAbsolutePath().toString()
                ),
                cancellationCheck,
                () -> {
                }
            );
            if (exitCode != 0) {
                throw IsolatedJavaWorker.readFailure(
                    exitCode,
                    errorFile,
                    "JPEG_VALIDATION_RESOURCE_LIMIT_EXCEEDED",
                    "The isolated JPEG validator exited before completing",
                    logger
                );
            }
            success = true;
            return new ValidationArtifacts(copies);
        } catch (RuntimeException exception) {
            failure = exception;
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

    private IsolatedJavaWorker.Spec workerSpec() {
        return new IsolatedJavaWorker.Spec(
            JpegValidationWorkerMain.class,
            properties.getWorkerHeapBytes(),
            properties.getWorkerTimeout(),
            "JPEG_VALIDATION_WORKER_START_FAILED",
            "The isolated JPEG validator could not be started",
            "JPEG_VALIDATION_TIMEOUT",
            "JPEG validation exceeded the configured time limit"
        );
    }
}
