package com.pdftools.operations.pdfa;

import com.pdftools.operations.OperationCancelledException;
import com.pdftools.operations.OperationException;
import com.pdftools.operations.OperationInput;
import com.pdftools.operations.shared.worker.IsolatedJavaWorker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.function.IntConsumer;

@Component
public class PdfAEngine {

    private static final Logger logger =
        LoggerFactory.getLogger(PdfAEngine.class);

    private final PdfAProperties properties;
    private final PdfADocumentValidator documentValidator;
    private final PdfAPlanFactory planFactory;
    private final PdfAConverter converter;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PdfAEngine(
            PdfAProperties properties,
            PdfADocumentValidator documentValidator,
            PdfAPlanFactory planFactory,
            PdfAConverter converter) {
        this.properties = properties;
        this.documentValidator = documentValidator;
        this.planFactory = planFactory;
        this.converter = converter;
    }

    public PdfAResult convert(
            OperationInput input,
            JsonNode options,
            Path workspace,
            IntConsumer progress,
            Runnable cancellationCheck) {
        validateConfiguration();
        PdfAPlanFactory.PdfAPlan plan = planFactory.create(options);
        documentValidator.validateSource(
            input.path(),
            workspace,
            cancellationCheck
        );
        progress.accept(2);
        Path pdf = null;
        Path report = workspace.resolve("pdfa-validation-report.json");
        try {
            pdf = converter.convert(
                input,
                options,
                workspace,
                value -> progress.accept(Math.min(
                    70,
                    5 + (int) Math.floor(65.0 * value / 97)
                )),
                cancellationCheck
            );
            documentValidator.validateOutput(
                pdf,
                workspace,
                cancellationCheck
            );
            progress.accept(72);
            validateWithVeraPdf(
                pdf,
                report,
                plan.profile(),
                workspace,
                cancellationCheck
            );
            validateReport(report, plan.profile());
            progress.accept(95);
            return new PdfAResult(pdf, report, plan.profile().option());
        } catch (OperationCancelledException exception) {
            cleanup(pdf, report, exception);
            throw exception;
        } catch (OperationException exception) {
            cleanup(pdf, report, exception);
            throw exception;
        } catch (RuntimeException exception) {
            OperationException failure = new OperationException(
                "PDFA_CONVERSION_FAILED",
                "The PDF could not be converted to PDF/A",
                exception
            );
            cleanup(pdf, report, failure);
            throw failure;
        }
    }

    private void validateWithVeraPdf(
            Path pdf,
            Path report,
            PdfAPlanFactory.PdfAProfile profile,
            Path workspace,
            Runnable cancellationCheck) {
        Path request = workspace.resolve(".pdfa-validation-request.bin");
        Path error = workspace.resolve(".pdfa-validation-error");
        PdfAValidationRequest.write(
            request,
            pdf,
            report,
            profile,
            properties
        );
        int exitCode = IsolatedJavaWorker.run(
            workerSpec(),
            List.of(
                request.toAbsolutePath().toString(),
                error.toAbsolutePath().toString()
            ),
            workspace,
            cancellationCheck,
            () -> {
            }
        );
        if (exitCode != 0) {
            throw IsolatedJavaWorker.readFailure(
                exitCode,
                error,
                "PDFA_VALIDATOR_FAILED",
                "The isolated veraPDF validator exited early",
                logger
            );
        }
    }

    private IsolatedJavaWorker.Spec workerSpec() {
        return new IsolatedJavaWorker.Spec(
            PdfAValidationWorkerMain.class,
            properties.getValidatorHeapBytes(),
            properties.getValidatorTimeout(),
            "PDFA_VALIDATOR_START_FAILED",
            "The isolated veraPDF validator could not be started",
            "PDFA_VALIDATOR_TIMEOUT",
            "veraPDF validation exceeded its time limit",
            validatorJvmArguments()
        );
    }

    static List<String> validatorJvmArguments() {
        return Runtime.version().feature() >= 26
            ? List.of("--enable-final-field-mutation=ALL-UNNAMED")
            : List.of();
    }

    private void validateReport(
            Path report,
            PdfAPlanFactory.PdfAProfile profile) {
        try {
            if (Files.isSymbolicLink(report)
                    || !Files.isRegularFile(
                        report,
                        LinkOption.NOFOLLOW_LINKS)
                    || Files.size(report) < 1
                    || Files.size(report)
                        > properties.getMaxReportBytes()) {
                throw invalidReport(null);
            }
            JsonNode value = objectMapper.readTree(report.toFile());
            if (!value.path("status").asText().equals("compliant")
                    || !value.path("profile").asText()
                        .equals(profile.option())
                    || !value.path("compliant").asBoolean(false)
                    || value.path("totalAssertions").asInt(-1) < 1
                    || value.path("failedChecks").asLong(-1) != 0
                    || !value.path("failures").isArray()
                    || !value.path("failures").isEmpty()) {
                throw invalidReport(null);
            }
        } catch (OperationException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw invalidReport(exception);
        }
    }

    private void validateConfiguration() {
        if (properties.getMaxInputBytes() < 1
                || properties.getMaxOutputBytes() < 1
                || properties.getMaxRuleFailures() < 1
                || properties.getMaxFailureCharacters() < 8
                || properties.getMaxReportBytes() < 1
                || properties.getValidatorHeapBytes()
                    < 32L * 1024L * 1024L
                || properties.getValidatorTimeout() == null
                || properties.getValidatorTimeout().isZero()
                || properties.getValidatorTimeout().isNegative()) {
            throw new IllegalStateException(
                "PDF/A validation limits are invalid"
            );
        }
    }

    private void cleanup(
            Path pdf,
            Path report,
            RuntimeException failure) {
        if (pdf != null) {
            delete(pdf, failure);
        }
        delete(report, failure);
    }

    private void delete(Path path, RuntimeException failure) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            failure.addSuppressed(exception);
            logger.error(
                "Could not remove partial PDF/A artifact {}",
                path,
                exception
            );
        }
    }

    private OperationException invalidReport(Throwable cause) {
        return new OperationException(
            "INVALID_PDFA_VALIDATION_REPORT",
            "The veraPDF validation report is invalid",
            cause
        );
    }

    public record PdfAResult(
        Path pdf,
        Path report,
        String profile
    ) {
    }
}
