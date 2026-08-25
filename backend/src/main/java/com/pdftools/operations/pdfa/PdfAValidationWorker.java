package com.pdftools.operations.pdfa;

import com.pdftools.operations.BoundedOutputStream;
import com.pdftools.operations.OperationException;
import com.pdftools.operations.OutputLimitExceededException;
import org.verapdf.core.EncryptedPdfException;
import org.verapdf.core.ModelParsingException;
import org.verapdf.core.ValidationException;
import org.verapdf.pdfa.Foundries;
import org.verapdf.pdfa.PDFAParser;
import org.verapdf.pdfa.PDFAValidator;
import org.verapdf.pdfa.results.TestAssertion;
import org.verapdf.pdfa.results.ValidationResult;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBufferedFile;
import org.apache.pdfbox.io.RandomAccessStreamCache;
import org.apache.pdfbox.io.ScratchFile;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdfwriter.compress.CompressParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

final class PdfAValidationWorker {

    private static final Logger logger =
        LoggerFactory.getLogger(PdfAValidationWorker.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    void validate(PdfAValidationRequest request) {
        requireSource(request);
        Path fixed = request.report().resolveSibling(
            ".pdfa-metadata-normalized.pdf"
        );
        RuntimeException failure = null;
        try {
            MetadataNormalization metadata = normalizeMetadata(
                request,
                fixed
            );
            if (metadata.applied()) {
                move(fixed, request.source());
                requireSource(request);
            }
            ValidationResult result = validateDocument(request);
            List<RuleFailure> failures = failures(result, request);
            long failedChecks = failedChecks(result);
            ValidationReport report = new ValidationReport(
                result.isCompliant() ? "compliant" : "non-compliant",
                request.profile().option(),
                result.isCompliant(),
                result.getTotalAssertions(),
                failedChecks,
                failures,
                metadata.applied(),
                metadata.changes()
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
            failure = exception;
            throw exception;
        } catch (EncryptedPdfException exception) {
            failure = new OperationException(
                "ENCRYPTED_PDFA_OUTPUT",
                "The PDF/A candidate is unexpectedly encrypted",
                exception
            );
            throw failure;
        } catch (ModelParsingException
                | ValidationException
                | IOException exception) {
            failure = new OperationException(
                "PDFA_VALIDATOR_FAILED",
                "veraPDF could not validate the converted document",
                exception
            );
            throw failure;
        } catch (RuntimeException exception) {
            failure = exception;
            throw exception;
        } finally {
            delete(fixed, failure);
        }
    }

    private ValidationResult validateDocument(
            PdfAValidationRequest request)
            throws IOException, ModelParsingException,
            EncryptedPdfException, ValidationException {
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
            return result;
        }
    }

    private MetadataNormalization normalizeMetadata(
            PdfAValidationRequest request,
            Path fixed) throws IOException {
        Path scratch = request.report().getParent().resolve(
            ".pdfa-metadata-scratch"
        );
        Files.createDirectories(scratch);
        RandomAccessStreamCache.StreamCacheCreateFunction scratchCache =
            () -> new ScratchFile(scratch.toFile());
        try (RandomAccessReadBufferedFile randomAccess =
                 new RandomAccessReadBufferedFile(request.source());
             PDDocument document = Loader.loadPDF(
                 randomAccess,
                 scratchCache
             )) {
            var information = document.getDocumentInformation();
            if (information.getCreationDate() == null
                    && information.getModificationDate() == null) {
                return MetadataNormalization.none();
            }
            information.setCreationDate(null);
            information.setModificationDate(null);
            try (OutputStream fileOutput = Files.newOutputStream(
                    fixed,
                    StandardOpenOption.CREATE_NEW);
                 BoundedOutputStream bounded = new BoundedOutputStream(
                     fileOutput,
                     request.maxSourceBytes(),
                     () -> {
                     }
                 )) {
                document.save(
                    bounded,
                    request.profile()
                        == PdfAPlanFactory.PdfAProfile.PDFA_1_B
                            ? CompressParameters.NO_COMPRESSION
                            : CompressParameters.DEFAULT_COMPRESSION
                );
            }
            return new MetadataNormalization(
                true,
                List.of(
                    "Removed document Info creation and modification "
                        + "dates before conformance validation"
                )
            );
        } catch (OutputLimitExceededException exception) {
            throw new OperationException(
                "PDFA_METADATA_NORMALIZATION_LIMIT_EXCEEDED",
                "PDF/A metadata normalization exceeds the output limit",
                exception
            );
        }
    }

    private void move(Path source, Path destination) throws IOException {
        try {
            Files.move(
                source,
                destination,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            );
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(
                source,
                destination,
                StandardCopyOption.REPLACE_EXISTING
            );
        }
    }

    private void delete(Path path, RuntimeException failure) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            if (failure == null) {
                throw new OperationException(
                    "PDFA_METADATA_NORMALIZATION_CLEANUP_FAILED",
                    "PDF/A metadata scratch could not be removed",
                    exception
                );
            }
            failure.addSuppressed(exception);
            logger.error(
                "Could not remove veraPDF metadata scratch {}",
                path,
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
        List<RuleFailure> failures,
        boolean metadataNormalized,
        List<String> metadataChanges
    ) {
    }

    record RuleFailure(
        String ruleId,
        String location,
        String message
    ) {
    }

    private record MetadataNormalization(
        boolean applied,
        List<String> changes
    ) {
        private static MetadataNormalization none() {
            return new MetadataNormalization(false, List.of());
        }
    }
}
