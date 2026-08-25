package com.pdftools.operations.repair;

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
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.IntConsumer;

@Component
public class RepairPdfEngine {

    private static final Logger logger =
        LoggerFactory.getLogger(RepairPdfEngine.class);
    private static final long PROCESS_SPEC_HEAP_BYTES =
        32L * 1024L * 1024L;

    private final RepairPdfProperties properties;
    private final RepairProcessSandbox sandbox;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RepairPdfEngine(RepairPdfProperties properties) {
        this.properties = properties;
        this.sandbox = new RepairProcessSandbox(properties);
    }

    public RepairResult repair(
            Path source,
            Path workspace,
            IntConsumer progress,
            Runnable cancellationCheck) {
        validateConfiguration();
        validateSource(source);
        Path output = workspace.resolve("repaired.pdf");
        Path report = workspace.resolve("repair-report.json");
        Path networkFilter = workspace.resolve(".repair-network.bpf");
        List<String> warnings = new ArrayList<>();
        try {
            progress.accept(5);
            PhaseResult encryption = run(
                "encryption",
                workspace,
                networkFilter,
                List.of(
                    "--is-encrypted",
                    source.toAbsolutePath().toString()
                ),
                properties.getCheckTimeout(),
                properties.getMaxLogBytes(),
                cancellationCheck
            );
            if (encryption.exitCode() == 0) {
                throw new OperationException(
                    "ENCRYPTED_PDF_NOT_SUPPORTED",
                    "Repair PDF requires an unencrypted PDF"
                );
            }
            progress.accept(10);
            PhaseResult rewrite = run(
                "rewrite",
                workspace,
                networkFilter,
                List.of(
                    "--deterministic-id",
                    "--object-streams=generate",
                    "--compress-streams=y",
                    "--recompress-flate",
                    source.toAbsolutePath().toString(),
                    output.toAbsolutePath().toString()
                ),
                properties.getRewriteTimeout(),
                properties.getMaxOutputBytes(),
                cancellationCheck
            );
            warnings.addAll(rewrite.warnings());
            if (rewrite.exitCode() != 0
                    && rewrite.exitCode() != 3) {
                throw rewriteFailure(rewrite);
            }
            progress.accept(70);
            int pages = validateOutput(
                output,
                workspace,
                cancellationCheck
            );
            PhaseResult check = run(
                "check",
                workspace,
                networkFilter,
                List.of(
                    "--check",
                    output.toAbsolutePath().toString()
                ),
                properties.getCheckTimeout(),
                properties.getMaxLogBytes(),
                cancellationCheck
            );
            warnings.addAll(check.warnings());
            if (check.exitCode() != 0 && check.exitCode() != 3) {
                throw new OperationException(
                    "PDF_REPAIR_VALIDATION_FAILED",
                    "qpdf could not validate the repaired PDF"
                );
            }
            progress.accept(90);
            List<String> normalized = normalizedWarnings(
                warnings,
                source,
                output,
                workspace
            );
            boolean partial = rewrite.exitCode() == 3
                || check.exitCode() == 3
                || !normalized.isEmpty();
            if (partial && normalized.isEmpty()) {
                normalized = List.of(
                    "qpdf completed recovery with unspecified warnings"
                );
            }
            writeReport(report, partial, pages, normalized);
            progress.accept(95);
            return new RepairResult(
                output,
                report,
                partial ? "partially-recovered" : "repaired"
            );
        } catch (OperationCancelledException exception) {
            cleanup(output, report, exception);
            throw exception;
        } catch (OperationException exception) {
            cleanup(output, report, exception);
            throw exception;
        } catch (IOException | RuntimeException exception) {
            OperationException failure = new OperationException(
                "PDF_REPAIR_FAILED",
                "The PDF could not be structurally repaired",
                exception
            );
            cleanup(output, report, failure);
            throw failure;
        }
    }

    private PhaseResult run(
            String phase,
            Path workspace,
            Path networkFilter,
            List<String> arguments,
            Duration timeout,
            long maxFileBytes,
            Runnable cancellationCheck) {
        Path stdout = workspace.resolve(".repair-" + phase + ".stdout");
        Path stderr = workspace.resolve(".repair-" + phase + ".stderr");
        deleteIfExists(stdout, null);
        deleteIfExists(stderr, null);
        int exitCode = IsolatedJavaWorker.runCommand(
            processSpec(phase, timeout),
            sandbox.command(
                workspace,
                networkFilter,
                arguments,
                maxFileBytes
            ),
            workspace,
            sandbox.environment(workspace),
            stdout,
            stderr,
            cancellationCheck,
            () -> enforceLogLimit(stdout, stderr)
        );
        enforceLogLimit(stdout, stderr);
        return new PhaseResult(exitCode, readWarnings(stderr));
    }

    private IsolatedJavaWorker.Spec processSpec(
            String phase,
            Duration timeout) {
        return new IsolatedJavaWorker.Spec(
            RepairPdfEngine.class,
            PROCESS_SPEC_HEAP_BYTES,
            timeout,
            "QPDF_START_FAILED",
            "qpdf could not be started",
            "QPDF_TIMEOUT",
            "qpdf " + phase + " exceeded its time limit"
        );
    }

    private void enforceLogLimit(Path stdout, Path stderr) {
        try {
            long bytes = Math.addExact(
                sizeIfPresent(stdout),
                sizeIfPresent(stderr)
            );
            if (bytes > properties.getMaxLogBytes()) {
                throw new OperationException(
                    "PDF_REPAIR_LOG_LIMIT_EXCEEDED",
                    "qpdf produced too much diagnostic output"
                );
            }
        } catch (ArithmeticException exception) {
            throw new OperationException(
                "PDF_REPAIR_LOG_LIMIT_EXCEEDED",
                "qpdf produced too much diagnostic output",
                exception
            );
        } catch (IOException exception) {
            throw new OperationException(
                "PDF_REPAIR_LOG_READ_FAILED",
                "qpdf diagnostics could not be inspected",
                exception
            );
        }
    }

    private long sizeIfPresent(Path path) throws IOException {
        return Files.exists(path, LinkOption.NOFOLLOW_LINKS)
            ? Files.size(path)
            : 0;
    }

    private List<String> readWarnings(Path path) {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            return List.of();
        }
        try {
            return Files.readAllLines(path, StandardCharsets.UTF_8)
                .stream()
                .map(String::strip)
                .filter(line -> !line.isEmpty())
                .toList();
        } catch (IOException exception) {
            throw new OperationException(
                "PDF_REPAIR_LOG_READ_FAILED",
                "qpdf diagnostics could not be read",
                exception
            );
        }
    }

    private List<String> normalizedWarnings(
            List<String> warnings,
            Path source,
            Path output,
            Path workspace) {
        Set<String> unique = new LinkedHashSet<>();
        for (String warning : warnings) {
            if (unique.size() >= properties.getMaxWarnings() - 1) {
                unique.add("Additional qpdf warnings were truncated");
                break;
            }
            String normalized = warning
                .replace(source.toAbsolutePath().toString(), "input.pdf")
                .replace(output.toAbsolutePath().toString(), "repaired.pdf")
                .replace(workspace.toAbsolutePath().toString(), ".");
            if (normalized.length()
                    > properties.getMaxWarningCharacters()) {
                normalized = normalized.substring(
                    0,
                    properties.getMaxWarningCharacters() - 1
                ) + ".";
            }
            unique.add(normalized);
        }
        return List.copyOf(unique);
    }

    private void writeReport(
            Path report,
            boolean partial,
            int pages,
            List<String> warnings) throws IOException {
        RepairReport value = new RepairReport(
            partial ? "partially-recovered" : "repaired",
            partial
                ? "qpdf recovered the PDF with warnings; review the "
                    + "document for missing or altered content"
                : "qpdf rewrote and validated the PDF structure",
            pages,
            warnings
        );
        byte[] json = objectMapper.writeValueAsBytes(value);
        if (json.length > properties.getMaxReportBytes()) {
            throw new OperationException(
                "PDF_REPAIR_REPORT_LIMIT_EXCEEDED",
                "The repair report exceeds the configured limit"
            );
        }
        Files.write(report, json);
    }

    private int validateOutput(
            Path output,
            Path workspace,
            Runnable cancellationCheck) throws IOException {
        PdfInputValidator.requirePdfHeader(output);
        if (Files.isSymbolicLink(output)
                || !Files.isRegularFile(
                    output,
                    LinkOption.NOFOLLOW_LINKS)
                || Files.size(output) < 1
                || Files.size(output) > properties.getMaxOutputBytes()) {
            throw new OperationException(
                "INVALID_REPAIRED_PDF",
                "qpdf did not produce a valid repaired PDF"
            );
        }
        Path scratch = workspace.resolve(".repair-pdfbox-scratch");
        Files.createDirectories(scratch);
        RandomAccessStreamCache.StreamCacheCreateFunction scratchCache =
            () -> new ScratchFile(scratch.toFile());
        try (RandomAccessReadBufferedFile randomAccess =
                 new RandomAccessReadBufferedFile(output);
             PDDocument document = Loader.loadPDF(
                 randomAccess,
                 scratchCache
             )) {
            cancellationCheck.run();
            if (document.isEncrypted()) {
                throw new OperationException(
                    "INVALID_REPAIRED_PDF",
                    "The repaired PDF is unexpectedly encrypted"
                );
            }
            List<org.apache.pdfbox.pdmodel.PDPage> pages =
                new PdfPageTreeReader(properties).read(
                    document,
                    cancellationCheck
                ).pages();
            if (pages.isEmpty()) {
                throw new OperationException(
                    "INVALID_REPAIRED_PDF",
                    "The repaired PDF contains no pages"
                );
            }
            return pages.size();
        } catch (InvalidPasswordException exception) {
            throw new OperationException(
                "INVALID_REPAIRED_PDF",
                "The repaired PDF is unexpectedly encrypted",
                exception
            );
        }
    }

    private void validateSource(Path source) {
        try {
            if (Files.isSymbolicLink(source)
                    || !Files.isRegularFile(
                        source,
                        LinkOption.NOFOLLOW_LINKS)
                    || Files.size(source) < 1
                    || Files.size(source)
                        > properties.getMaxInputBytes()) {
                throw new OperationException(
                    "REPAIR_INPUT_LIMIT_EXCEEDED",
                    "The PDF exceeds the configured repair input limit"
                );
            }
            requirePdfMarker(source);
        } catch (OperationException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new OperationException(
                "INVALID_PDF",
                "The input PDF could not be read",
                exception
            );
        }
    }

    private void requirePdfMarker(Path source) throws IOException {
        byte[] prefix = new byte[1024];
        int length;
        try (InputStream input = Files.newInputStream(source)) {
            length = input.read(prefix);
        }
        byte[] marker = "%PDF-".getBytes(StandardCharsets.US_ASCII);
        for (int offset = 0;
                offset <= length - marker.length;
                offset++) {
            boolean matches = true;
            for (int index = 0; index < marker.length; index++) {
                matches &= prefix[offset + index] == marker[index];
            }
            if (matches) {
                return;
            }
        }
        throw new OperationException(
            "INVALID_PDF",
            "The input does not contain a PDF header"
        );
    }

    private void validateConfiguration() {
        if (properties.getMaxInputBytes() < 1
                || properties.getMaxOutputBytes()
                    < properties.getMaxInputBytes()
                || properties.getMaxLogBytes() < 1
                || properties.getMaxReportBytes() < 1
                || properties.getMaxWarnings() < 1
                || properties.getMaxWarningCharacters() < 2
                || properties.getRewriteTimeout() == null
                || properties.getRewriteTimeout().isZero()
                || properties.getRewriteTimeout().isNegative()
                || properties.getCheckTimeout() == null
                || properties.getCheckTimeout().isZero()
                || properties.getCheckTimeout().isNegative()) {
            throw new IllegalStateException(
                "PDF repair limits are invalid"
            );
        }
    }

    private OperationException rewriteFailure(PhaseResult result) {
        String diagnostics = String.join(
            "\n",
            result.warnings()
        ).toLowerCase(Locale.ROOT);
        logger.warn(
            "qpdf rewrite failed with exit code {}: {}",
            result.exitCode(),
            diagnostics
        );
        if (diagnostics.contains("invalid password")
                || diagnostics.contains("requires a password")) {
            return new OperationException(
                "ENCRYPTED_PDF_NOT_SUPPORTED",
                "Repair PDF requires an unencrypted PDF"
            );
        }
        return new OperationException(
            "PDF_REPAIR_FAILED",
            "qpdf could not recover the PDF structure"
        );
    }

    private void cleanup(
            Path output,
            Path report,
            RuntimeException failure) {
        deleteIfExists(output, failure);
        deleteIfExists(report, failure);
    }

    private void deleteIfExists(
            Path path,
            RuntimeException failure) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            if (failure != null) {
                failure.addSuppressed(exception);
            }
            logger.error(
                "Could not remove partial PDF repair artifact {}",
                path,
                exception
            );
        }
    }

    public record RepairResult(
        Path pdf,
        Path report,
        String status
    ) {
    }

    private record PhaseResult(
        int exitCode,
        List<String> warnings
    ) {
    }

    private record RepairReport(
        String status,
        String summary,
        int recoveredPages,
        List<String> warnings
    ) {
    }
}
