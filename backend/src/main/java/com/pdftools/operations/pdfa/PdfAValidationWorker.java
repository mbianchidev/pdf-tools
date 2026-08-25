package com.pdftools.operations.pdfa;

import com.pdftools.operations.OperationException;
import org.verapdf.core.EncryptedPdfException;
import org.verapdf.core.ModelParsingException;
import org.verapdf.core.ValidationException;
import org.verapdf.pdfa.Foundries;
import org.verapdf.pdfa.PDFAParser;
import org.verapdf.pdfa.PDFAValidator;
import org.verapdf.pdfa.results.TestAssertion;
import org.verapdf.pdfa.results.ValidationResult;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

final class PdfAValidationWorker {

    private final ObjectMapper objectMapper = new ObjectMapper();

    void validate(PdfAValidationRequest request) {
        requireSource(request);
        try (InputStream input = Files.newInputStream(request.source());
             PDFAParser parser = Foundries.defaultInstance().createParser(
                 input,
                 request.profile().flavour()
             );
             PDFAValidator validator = Foundries.defaultInstance()
                 .createValidator(
                     request.profile().flavour(),
                     false
                 )) {
            ValidationResult result = validator.validate(parser);
            if (result.getPDFAFlavour() != request.profile().flavour()) {
                throw validationFailure(
                    "veraPDF returned an unexpected conformance profile"
                );
            }
            List<RuleFailure> failures = failures(result, request);
            long failedChecks = failedChecks(result);
            ValidationReport report = new ValidationReport(
                result.isCompliant() ? "compliant" : "non-compliant",
                request.profile().option(),
                result.isCompliant(),
                result.getTotalAssertions(),
                failedChecks,
                failures
            );
            writeReport(request, report);
            if (!result.isCompliant()) {
                String firstRule = failures.isEmpty()
                    ? "unknown"
                    : failures.getFirst().ruleId();
                throw validationFailure(
                    "veraPDF found " + failedChecks
                        + " failing checks for "
                        + request.profile().option()
                        + "; first rule: " + firstRule
                );
            }
        } catch (OperationException exception) {
            throw exception;
        } catch (EncryptedPdfException exception) {
            throw new OperationException(
                "ENCRYPTED_PDFA_OUTPUT",
                "The PDF/A candidate is unexpectedly encrypted",
                exception
            );
        } catch (ModelParsingException
                | ValidationException
                | IOException exception) {
            throw new OperationException(
                "PDFA_VALIDATOR_FAILED",
                "veraPDF could not validate the converted document",
                exception
            );
        }
    }

    private List<RuleFailure> failures(
            ValidationResult result,
            PdfAValidationRequest request) {
        List<RuleFailure> failures = new ArrayList<>();
        for (TestAssertion assertion : result.getTestAssertions()) {
            if (assertion.getStatus() != TestAssertion.Status.FAILED
                    || failures.size() >= request.maxRuleFailures()) {
                continue;
            }
            failures.add(new RuleFailure(
                bounded(
                    assertion.getRuleId().toString(),
                    request.maxFailureCharacters()
                ),
                bounded(
                    assertion.getLocation() == null
                        ? ""
                        : assertion.getLocation().getContext(),
                    request.maxFailureCharacters()
                ),
                bounded(
                    message(assertion),
                    request.maxFailureCharacters()
                )
            ));
        }
        return List.copyOf(failures);
    }

    private String message(TestAssertion assertion) {
        if (assertion.getErrorMessage() != null
                && !assertion.getErrorMessage().isBlank()) {
            return assertion.getErrorMessage();
        }
        return assertion.getMessage() == null
            ? ""
            : assertion.getMessage();
    }

    private long failedChecks(ValidationResult result) {
        long total = 0;
        try {
            for (int count : result.getFailedChecks().values()) {
                total = Math.addExact(total, count);
            }
        } catch (ArithmeticException exception) {
            throw validationFailure(
                "veraPDF returned an invalid failure count"
            );
        }
        return total;
    }

    private String bounded(String value, int maxCharacters) {
        if (value == null) {
            return "";
        }
        String normalized = value
            .replace('\r', ' ')
            .replace('\n', ' ')
            .strip();
        return normalized.length() <= maxCharacters
            ? normalized
            : normalized.substring(0, maxCharacters - 3) + "...";
    }

    private void writeReport(
            PdfAValidationRequest request,
            ValidationReport report) throws IOException {
        byte[] json = objectMapper.writeValueAsBytes(report);
        if (json.length > request.maxReportBytes()) {
            throw new OperationException(
                "PDFA_REPORT_LIMIT_EXCEEDED",
                "The veraPDF validation report exceeds its limit"
            );
        }
        Files.write(
            request.report(),
            json,
            StandardOpenOption.CREATE_NEW
        );
    }

    private void requireSource(PdfAValidationRequest request) {
        Path source = request.source();
        if (Files.isSymbolicLink(source)
                || !Files.isRegularFile(
                    source,
                    LinkOption.NOFOLLOW_LINKS)
                || size(source) < 1
                || size(source) > request.maxSourceBytes()) {
            throw new OperationException(
                "PDFA_VALIDATOR_PROTOCOL_ERROR",
                "The PDF/A validator source is invalid"
            );
        }
    }

    private long size(Path source) {
        try {
            return Files.size(source);
        } catch (IOException exception) {
            throw new OperationException(
                "PDFA_VALIDATOR_PROTOCOL_ERROR",
                "The PDF/A validator source size could not be read",
                exception
            );
        }
    }

    private OperationException validationFailure(String message) {
        return new OperationException(
            "PDFA_VALIDATION_FAILED",
            message
        );
    }

    record ValidationReport(
        String status,
        String profile,
        boolean compliant,
        int totalAssertions,
        long failedChecks,
        List<RuleFailure> failures
    ) {
    }

    record RuleFailure(
        String ruleId,
        String location,
        String message
    ) {
    }
}
