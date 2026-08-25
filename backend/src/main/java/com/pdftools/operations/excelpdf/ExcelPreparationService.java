package com.pdftools.operations.excelpdf;

import com.pdftools.operations.OperationException;
import com.pdftools.operations.office.NativeProcessSandbox;
import com.pdftools.operations.office.OfficeConversionProperties;
import com.pdftools.operations.shared.worker.IsolatedJavaWorker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class ExcelPreparationService {

    private static final Logger logger =
        LoggerFactory.getLogger(ExcelPreparationService.class);

    private final ExcelProperties properties;
    private final OfficeConversionProperties officeProperties;
    private final NativeProcessSandbox sandbox;

    public ExcelPreparationService(
            ExcelProperties properties,
            OfficeConversionProperties officeProperties,
            NativeProcessSandbox sandbox) {
        this.properties = properties;
        this.officeProperties = officeProperties;
        this.sandbox = sandbox;
    }

    public Path prepare(
            Path source,
            Path destination,
            ExcelPlanFactory.ExcelPlan plan,
            org.apache.poi.ss.SpreadsheetVersion spreadsheetVersion,
            Path workspace,
            Runnable cancellationCheck) {
        Path workspaceRoot = workspace.toAbsolutePath().normalize();
        if (!destination.toAbsolutePath().normalize()
                .startsWith(workspaceRoot)) {
            throw new OperationException(
                "EXCEL_PREPARATION_PROTOCOL_ERROR",
                "The prepared workbook destination is outside its workspace"
            );
        }
        Path workerRoot = workspace.resolve(".excel-preparation");
        Path workerSource = workerRoot.resolve("source");
        Path request = workerRoot.resolve("request.bin");
        Path error = workerRoot.resolve("error.txt");
        Path networkFilter = workerRoot.resolve("network-filter.bpf");
        RuntimeException failure = null;
        try {
            Files.createDirectories(workerRoot);
            Files.copy(
                source,
                workerSource,
                StandardCopyOption.REPLACE_EXISTING
            );
            ExcelPreparationRequest.write(
                request,
                workerSource,
                destination,
                plan,
                spreadsheetVersion,
                properties
            );
            sandbox.prepareWorkspace(workspace, officeProperties);
            IsolatedJavaWorker.Spec spec = workerSpec();
            List<String> javaCommand = IsolatedJavaWorker.command(
                spec,
                List.of(
                    request.toAbsolutePath().toString(),
                    error.toAbsolutePath().toString()
                ),
                workerRoot
            );
            int exitCode = IsolatedJavaWorker.runCommand(
                spec,
                sandbox.command(
                    workspace,
                    networkFilter,
                    Path.of(javaCommand.getFirst()),
                    javaCommand.subList(1, javaCommand.size()),
                    officeProperties,
                    javaReadRoots(),
                    properties.getWorkerAddressSpaceBytes()
                ),
                workerRoot,
                workerEnvironment(workerRoot),
                cancellationCheck,
                () -> {
                }
            );
            if (exitCode != 0) {
                throw IsolatedJavaWorker.readFailure(
                    exitCode,
                    error,
                    "EXCEL_PREPARATION_RESOURCE_LIMIT_EXCEEDED",
                    "Excel workbook preparation exceeded its resources",
                    logger
                );
            }
            if (Files.isSymbolicLink(destination)
                    || !Files.isRegularFile(
                        destination,
                        LinkOption.NOFOLLOW_LINKS)
                    || Files.size(destination) < 1
                    || Files.size(destination)
                        > properties.getMaxPreparedBytes()) {
                throw new OperationException(
                    "EXCEL_PREPARATION_PROTOCOL_ERROR",
                    "The Excel preparation worker returned invalid output"
                );
            }
            return destination;
        } catch (OperationException exception) {
            failure = exception;
            throw exception;
        } catch (IOException exception) {
            failure = new OperationException(
                "EXCEL_PREPARATION_PROTOCOL_ERROR",
                "The prepared workbook could not be inspected",
                exception
            );
            throw failure;
        } finally {
            cleanup(request, failure);
            cleanup(error, failure);
            cleanup(networkFilter, failure);
            cleanup(workerSource, failure);
            cleanupTree(workerRoot, failure);
            if (failure != null) {
                cleanup(destination, failure);
            }
        }
    }

    private IsolatedJavaWorker.Spec workerSpec() {
        return new IsolatedJavaWorker.Spec(
            ExcelPreparationWorkerMain.class,
            properties.getWorkerHeapBytes(),
            properties.getWorkerTimeout(),
            "EXCEL_PREPARATION_WORKER_START_FAILED",
            "The Excel preparation worker could not be started",
            "EXCEL_PREPARATION_TIMEOUT",
            "Excel workbook preparation exceeded its time limit"
        );
    }

    private List<Path> javaReadRoots() {
        List<Path> roots = new ArrayList<>();
        roots.add(Path.of(System.getProperty("java.home")));
        String classpath = System.getProperty(
            "surefire.test.class.path",
            System.getProperty("java.class.path")
        );
        for (String entry : classpath.split(
                java.util.regex.Pattern.quote(File.pathSeparator))) {
            if (!entry.isBlank()) {
                roots.add(Path.of(entry));
            }
        }
        return List.copyOf(roots);
    }

    private Map<String, String> workerEnvironment(Path workerRoot) {
        String root = workerRoot.toAbsolutePath().toString();
        return Map.of(
            "HOME", root,
            "TMPDIR", root,
            "LANG", "C.UTF-8",
            "LC_ALL", "C.UTF-8",
            "PATH", "/usr/local/bin:/usr/bin:/bin"
        );
    }

    private void cleanup(Path path, RuntimeException failure) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            OperationException cleanupFailure = new OperationException(
                "EXCEL_PREPARATION_CLEANUP_FAILED",
                "Excel preparation scratch could not be removed",
                exception
            );
            if (failure != null) {
                failure.addSuppressed(cleanupFailure);
            }
            logger.error(
                "Could not remove Excel preparation scratch {}",
                path,
                cleanupFailure
            );
        }
    }

    private void cleanupTree(Path root, RuntimeException failure) {
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            for (Path path : paths
                    .sorted(java.util.Comparator.reverseOrder())
                    .toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException exception) {
            OperationException cleanupFailure = new OperationException(
                "EXCEL_PREPARATION_CLEANUP_FAILED",
                "Excel preparation scratch could not be removed",
                exception
            );
            if (failure != null) {
                failure.addSuppressed(cleanupFailure);
            }
            logger.error(
                "Could not remove Excel preparation scratch {}",
                root,
                cleanupFailure
            );
        }
    }
}
