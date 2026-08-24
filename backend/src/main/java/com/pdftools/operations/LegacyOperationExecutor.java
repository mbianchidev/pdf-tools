package com.pdftools.operations;

import com.pdftools.config.JobProperties;
import com.pdftools.dto.PdfOperationResult;
import com.pdftools.exception.PdfProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Component
public class LegacyOperationExecutor {

    private static final Logger logger =
        LoggerFactory.getLogger(LegacyOperationExecutor.class);

    private final JobProperties jobProperties;
    private final LegacyWorkspaceRegistry workspaceRegistry;

    public LegacyOperationExecutor(
            JobProperties jobProperties,
            LegacyWorkspaceRegistry workspaceRegistry) {
        this.jobProperties = jobProperties;
        this.workspaceRegistry = workspaceRegistry;
    }

    public PdfOperationResult executeSinglePdf(
            PdfOperation operation,
            MultipartFile file,
            JsonNode options,
            String sourceFilename,
            Path outputDirectory,
            String workspacePrefix,
            String outputPrefix,
            String outputSuffix,
            String successMessage,
            LegacyOperationGuard guard) throws PdfProcessingException {
        Path workspace = null;
        Path outputPath = null;
        FileChannel lockChannel = null;
        FileLock lock = null;
        try {
            validateUpload(operation, file, options, sourceFilename);
            guard.checkCancelled();
            Files.createDirectories(jobProperties.getWorkRoot());
            Files.createDirectories(outputDirectory);
            workspace = Files.createTempDirectory(
                jobProperties.getWorkRoot(),
                workspacePrefix
            );
            lockChannel = FileChannel.open(
                workspace.resolve(".active.lock"),
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE
            );
            lock = lockChannel.lock();
            workspaceRegistry.register(workspace);

            Path inputPath = workspace.resolve("input-0001.bin");
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, inputPath);
            }
            OperationContext context = new OperationContext(
                UUID.randomUUID(),
                options,
                List.of(new OperationInput(
                    1,
                    inputPath,
                    sourceFilename,
                    mediaType(file),
                    Files.size(inputPath),
                    "legacy"
                )),
                workspace,
                ignored -> {
                },
                () -> false
            );
            List<OperationOutput> outputs = operation.execute(
                guardedContext(context, guard)
            );
            if (outputs.size() != 1) {
                throw new OperationException(
                    "INVALID_OUTPUT_COUNT",
                    "Legacy operation must produce exactly one output"
                );
            }
            Path generated = validateOutputPath(
                workspace,
                outputs.getFirst().path()
            );
            outputPath = reserveOutput(
                outputDirectory,
                outputPrefix,
                outputSuffix
            );
            guard.own(outputPath);
            Files.move(
                generated,
                outputPath,
                StandardCopyOption.REPLACE_EXISTING
            );
            guard.checkCancelled();
            return new PdfOperationResult(
                true,
                successMessage,
                outputPath.getFileName().toString()
            );
        } catch (OperationCancelledException exception) {
            deleteOutput(outputPath);
            throw new PdfProcessingException(
                operation.key() + " was cancelled",
                exception
            );
        } catch (OperationException exception) {
            deleteOutput(outputPath);
            throw new PdfProcessingException(
                exception.getMessage(),
                exception
            );
        } catch (Exception exception) {
            deleteOutput(outputPath);
            throw new PdfProcessingException(
                "Failed to run " + operation.key() + ": "
                    + exception.getMessage(),
                exception
            );
        } finally {
            release(lock, lockChannel);
            workspaceRegistry.unregister(workspace);
            deleteWorkspace(workspace);
        }
    }

    private void validateUpload(
            PdfOperation operation,
            MultipartFile file,
            JsonNode options,
            String sourceFilename) {
        if (file == null || file.isEmpty()) {
            throw new OperationException(
                "INVALID_FILE",
                "A non-empty PDF is required"
            );
        }
        if (file.getSize() > jobProperties.getMaxFileSizeBytes()) {
            throw new OperationException(
                "FILE_TOO_LARGE",
                "The PDF exceeds the configured file-size limit"
            );
        }
        operation.validateSubmission(new OperationSubmission(
            options,
            List.of(new OperationSubmission.UploadDescriptor(
                1,
                sourceFilename,
                mediaType(file),
                file.getSize()
            ))
        ));
    }

    private OperationContext guardedContext(
            OperationContext context,
            LegacyOperationGuard guard) {
        return new OperationContext(
            context.jobId(),
            context.options(),
            context.inputs(),
            context.workspace(),
            context::reportProgress,
            () -> {
                guard.checkCancelled();
                return false;
            }
        );
    }

    private Path validateOutputPath(Path workspace, Path output) {
        Path normalizedWorkspace = workspace.toAbsolutePath().normalize();
        Path normalizedOutput = output.toAbsolutePath().normalize();
        if (!normalizedOutput.startsWith(normalizedWorkspace)
                || !Files.isRegularFile(normalizedOutput)) {
            throw new OperationException(
                "INVALID_OUTPUT_PATH",
                "The operation produced an invalid output path"
            );
        }
        return normalizedOutput;
    }

    private Path reserveOutput(
            Path directory,
            String prefix,
            String suffix) throws IOException {
        for (int attempt = 0; attempt < 3; attempt++) {
            Path candidate = directory.resolve(
                prefix + UUID.randomUUID() + suffix
            );
            try {
                return Files.createFile(candidate);
            } catch (FileAlreadyExistsException ignored) {
                // Retry with a new opaque identifier.
            }
        }
        throw new IOException("Could not reserve a unique output filename");
    }

    private String mediaType(MultipartFile file) {
        return file.getContentType() == null
            ? "application/octet-stream"
            : file.getContentType().toLowerCase(Locale.ROOT);
    }

    private void deleteOutput(Path output) {
        if (output == null) {
            return;
        }
        try {
            Files.deleteIfExists(output);
        } catch (IOException exception) {
            logger.warn(
                "Failed to remove partial legacy output {}",
                output,
                exception
            );
        }
    }

    private void release(FileLock lock, FileChannel channel) {
        try {
            if (lock != null && lock.isValid()) {
                lock.release();
            }
        } catch (IOException exception) {
            logger.warn("Failed to release legacy workspace lock", exception);
        }
        try {
            if (channel != null) {
                channel.close();
            }
        } catch (IOException exception) {
            logger.warn(
                "Failed to close legacy workspace lock channel",
                exception
            );
        }
    }

    private void deleteWorkspace(Path workspace) {
        if (workspace == null || !Files.exists(workspace)) {
            return;
        }
        try (var paths = Files.walk(workspace)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException exception) {
                    logger.warn(
                        "Failed to remove legacy workspace path {}",
                        path,
                        exception
                    );
                }
            });
        } catch (IOException exception) {
            logger.warn(
                "Failed to inspect legacy workspace {}",
                workspace,
                exception
            );
        }
    }
}
