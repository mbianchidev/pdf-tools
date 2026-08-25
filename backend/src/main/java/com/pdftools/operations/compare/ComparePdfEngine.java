package com.pdftools.operations.compare;

import com.pdftools.operations.OperationCancelledException;
import com.pdftools.operations.OperationException;
import com.pdftools.operations.OperationInput;
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
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntConsumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Component
public class ComparePdfEngine {

    private static final Logger logger =
        LoggerFactory.getLogger(ComparePdfEngine.class);

    private final CompareProperties properties;
    private final ComparePlanFactory planFactory;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ComparePdfEngine(
            CompareProperties properties,
            ComparePlanFactory planFactory) {
        this.properties = properties;
        this.planFactory = planFactory;
    }

    public CompareResult compare(
            OperationInput baseline,
            OperationInput candidate,
            JsonNode options,
            Path workspace,
            IntConsumer progress,
            Runnable cancellationCheck) {
        ComparePlanFactory.ComparePlan plan =
            planFactory.create(options);
        int baselinePages = inspect(
            baseline.path(),
            workspace.resolve(".baseline-inspection"),
            cancellationCheck
        );
        int candidatePages = inspect(
            candidate.path(),
            workspace.resolve(".candidate-inspection"),
            cancellationCheck
        );
        int comparedPages = Math.max(baselinePages, candidatePages);
        Path report = workspace.resolve("comparison-report.json");
        Path archive = workspace.resolve("comparison.zip");
        Path request = workspace.resolve(".compare-request.bin");
        Path progressFile = workspace.resolve(".compare-progress");
        Path errorFile = workspace.resolve(".compare-error");
        CompareWorkerRequest.write(
            request,
            baseline.path(),
            candidate.path(),
            report,
            archive,
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
                workspace,
                cancellationCheck,
                () -> updateProgress(
                    progressFile,
                    comparedPages,
                    completed,
                    progress
                )
            );
        } catch (RuntimeException exception) {
            cleanup(report, archive, exception);
            throw exception;
        }
        if (exitCode != 0) {
            RuntimeException failure = IsolatedJavaWorker.readFailure(
                exitCode,
                errorFile,
                "COMPARE_RESOURCE_LIMIT_EXCEEDED",
                "The isolated PDF comparison worker exited early",
                logger
            );
            cleanup(report, archive, failure);
            throw failure;
        }
        validateOutputs(
            report,
            archive,
            baselinePages,
            candidatePages,
            comparedPages
        );
        progress.accept(95);
        return new CompareResult(report, archive);
    }

    private int inspect(
            Path source,
            Path scratch,
            Runnable cancellationCheck) {
        PdfInputValidator.requirePdfHeader(source);
        try {
            if (Files.isSymbolicLink(source)
                    || !Files.isRegularFile(
                        source,
                        LinkOption.NOFOLLOW_LINKS)
                    || Files.size(source) < 1
                    || Files.size(source) > properties.getMaxInputBytes()) {
                throw invalidPdf(null);
            }
            Files.createDirectories(scratch);
        } catch (OperationException exception) {
            throw exception;
        } catch (IOException exception) {
            throw invalidPdf(exception);
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
            List<org.apache.pdfbox.pdmodel.PDPage> pages =
                new PdfPageTreeReader(properties).read(
                    document,
                    cancellationCheck
                ).pages();
            if (pages.isEmpty()) {
                throw invalidPdf(null);
            }
            return pages.size();
        } catch (InvalidPasswordException exception) {
            throw encryptedPdf(exception);
        } catch (OperationException | OperationCancelledException exception) {
            throw exception;
        } catch (IOException exception) {
            throw invalidPdf(exception);
        }
    }

    private void validateOutputs(
            Path report,
            Path archive,
            int baselinePages,
            int candidatePages,
            int comparedPages) {
        try {
            requireFile(report, properties.getMaxReportBytes());
            requireFile(archive, properties.getMaxArchiveBytes());
            byte[] reportBytes = Files.readAllBytes(report);
            JsonNode value = objectMapper.readTree(reportBytes);
            JsonNode summary = value.path("summary");
            if (!Set.of("identical", "different").contains(
                    value.path("status").asText())
                    || summary.path("baselinePages").asInt(-1)
                        != baselinePages
                    || summary.path("candidatePages").asInt(-1)
                        != candidatePages
                    || summary.path("comparedPages").asInt(-1)
                        != comparedPages
                    || !value.path("pages").isArray()
                    || value.path("pages").size() != comparedPages) {
                throw invalidOutput(null);
            }
            validateArchive(
                archive,
                reportBytes,
                summary.path("visualChangedPages").asInt(-1),
                comparedPages
            );
        } catch (OperationException exception) {
            cleanup(report, archive, exception);
            throw exception;
        } catch (IOException | RuntimeException exception) {
            OperationException failure = invalidOutput(exception);
            cleanup(report, archive, failure);
            throw failure;
        }
    }

    private void validateArchive(
            Path archive,
            byte[] expectedReport,
            int expectedImages,
            int comparedPages) throws IOException {
        if (expectedImages < 0 || expectedImages > comparedPages) {
            throw invalidOutput(null);
        }
        Set<String> names = new HashSet<>();
        int images = 0;
        boolean report = false;
        long expanded = 0;
        byte[] buffer = new byte[8192];
        try (InputStream input = Files.newInputStream(archive);
             ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if (!names.add(name)
                        || names.size() > comparedPages + 1) {
                    throw invalidOutput(null);
                }
                ByteArrayOutputStream reportOutput =
                    name.equals("comparison-report.json")
                        ? new ByteArrayOutputStream()
                        : null;
                if (reportOutput == null
                        && !name.matches(
                            "visual/page-\\d{3,}-diff\\.png")) {
                    throw invalidOutput(null);
                }
                long entryBytes = 0;
                int read;
                while ((read = zip.read(buffer)) != -1) {
                    entryBytes = Math.addExact(entryBytes, read);
                    expanded = Math.addExact(expanded, read);
                    if (expanded > properties.getMaxArchiveBytes()
                            || (reportOutput != null
                                && entryBytes
                                    > properties.getMaxReportBytes())
                            || (reportOutput == null
                                && entryBytes
                                    > properties.getMaxDiffImageBytes())) {
                        throw invalidOutput(null);
                    }
                    if (reportOutput != null) {
                        reportOutput.write(buffer, 0, read);
                    }
                }
                if (reportOutput != null) {
                    report = Arrays.equals(
                        expectedReport,
                        reportOutput.toByteArray()
                    );
                } else {
                    images++;
                }
            }
        }
        if (!report || images != expectedImages) {
            throw invalidOutput(null);
        }
    }

    private void requireFile(Path path, long maxBytes) throws IOException {
        if (Files.isSymbolicLink(path)
                || !Files.isRegularFile(
                    path,
                    LinkOption.NOFOLLOW_LINKS)
                || Files.size(path) < 1
                || Files.size(path) > maxBytes) {
            throw invalidOutput(null);
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
                throw invalidOutput(null);
            }
            if (current > completed[0]) {
                completed[0] = current;
                progress.accept(Math.min(
                    90,
                    5 + (int) Math.floor(
                        85.0 * current / pageCount
                    )
                ));
            }
        } catch (NumberFormatException | IOException exception) {
            throw invalidOutput(exception);
        }
    }

    private IsolatedJavaWorker.Spec workerSpec() {
        return new IsolatedJavaWorker.Spec(
            ComparePdfWorkerMain.class,
            properties.getWorkerHeapBytes(),
            properties.getWorkerTimeout(),
            "COMPARE_WORKER_START_FAILED",
            "The isolated PDF comparison worker could not be started",
            "COMPARE_TIMEOUT",
            "PDF comparison exceeded its time limit"
        );
    }

    private void cleanup(
            Path report,
            Path archive,
            RuntimeException failure) {
        delete(report, failure);
        delete(archive, failure);
    }

    private void delete(Path path, RuntimeException failure) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            failure.addSuppressed(exception);
            logger.error(
                "Could not remove partial comparison output {}",
                path,
                exception
            );
        }
    }

    private OperationException encryptedPdf(Throwable cause) {
        return new OperationException(
            "ENCRYPTED_PDF_NOT_SUPPORTED",
            "Compare PDF requires two unencrypted PDFs",
            cause
        );
    }

    private OperationException invalidPdf(Throwable cause) {
        return new OperationException(
            "INVALID_PDF",
            "A comparison input is not a readable PDF",
            cause
        );
    }

    private OperationException invalidOutput(Throwable cause) {
        return new OperationException(
            "INVALID_COMPARE_OUTPUT",
            "The PDF comparison output is invalid",
            cause
        );
    }

    public record CompareResult(Path report, Path archive) {
    }
}
