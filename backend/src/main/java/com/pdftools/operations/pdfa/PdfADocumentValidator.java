package com.pdftools.operations.pdfa;

import com.pdftools.operations.OperationCancelledException;
import com.pdftools.operations.OperationException;
import com.pdftools.operations.OperationSubmission;
import com.pdftools.operations.shared.pdf.PdfInputValidator;
import com.pdftools.operations.shared.pdf.PdfPageTreeReader;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBufferedFile;
import org.apache.pdfbox.io.RandomAccessStreamCache;
import org.apache.pdfbox.io.ScratchFile;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class PdfADocumentValidator {

    private static final Set<String> MEDIA_TYPES = Set.of(
        "application/pdf",
        "application/octet-stream"
    );

    private final PdfAProperties properties;

    public PdfADocumentValidator(PdfAProperties properties) {
        this.properties = properties;
    }

    public void validateSubmission(OperationSubmission submission) {
        if (submission.files().size() != 1) {
            throw new OperationException(
                "INVALID_FILE_COUNT",
                "PDF to PDF/A requires exactly one PDF"
            );
        }
        OperationSubmission.UploadDescriptor file =
            submission.files().getFirst();
        if (!file.filename().toLowerCase(Locale.ROOT).endsWith(".pdf")
                || !MEDIA_TYPES.contains(
                    file.mediaType().toLowerCase(Locale.ROOT))) {
            throw new OperationException(
                "INVALID_PDF_FILE",
                "PDF to PDF/A accepts one PDF file"
            );
        }
        if (file.sizeBytes() < 1
                || file.sizeBytes() > properties.getMaxInputBytes()) {
            throw new OperationException(
                "PDFA_INPUT_LIMIT_EXCEEDED",
                "The PDF exceeds the configured PDF/A input limit"
            );
        }
    }

    public int validateSource(
            Path source,
            Path workspace,
            Runnable cancellationCheck) {
        return validate(
            source,
            properties.getMaxInputBytes(),
            workspace.resolve(".pdfa-source-inspection"),
            cancellationCheck,
            true
        );
    }

    public int validateOutput(
            Path output,
            Path workspace,
            Runnable cancellationCheck) {
        return validate(
            output,
            properties.getMaxOutputBytes(),
            workspace.resolve(".pdfa-output-inspection"),
            cancellationCheck,
            false
        );
    }

    private int validate(
            Path source,
            long maxBytes,
            Path scratch,
            Runnable cancellationCheck,
            boolean input) {
        PdfInputValidator.requirePdfHeader(source);
        requireFile(source, maxBytes, input);
        try {
            Files.createDirectories(scratch);
        } catch (IOException exception) {
            throw new OperationException(
                "PDFA_SCRATCH_FAILED",
                "PDF/A validation scratch could not be created",
                exception
            );
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
                throw input
                    ? encryptedPdf(null)
                    : invalidOutput(null);
            }
            List<org.apache.pdfbox.pdmodel.PDPage> pages =
                new PdfPageTreeReader(properties).read(
                    document,
                    cancellationCheck
                ).pages();
            if (pages.isEmpty()) {
                throw input ? invalidPdf(null) : invalidOutput(null);
            }
            return pages.size();
        } catch (InvalidPasswordException exception) {
            throw input
                ? encryptedPdf(exception)
                : invalidOutput(exception);
        } catch (OperationException | OperationCancelledException exception) {
            throw exception;
        } catch (IOException exception) {
            throw input
                ? invalidPdf(exception)
                : invalidOutput(exception);
        }
    }

    private void requireFile(
            Path path,
            long maxBytes,
            boolean input) {
        try {
            if (Files.isSymbolicLink(path)
                    || !Files.isRegularFile(
                        path,
                        LinkOption.NOFOLLOW_LINKS)
                    || Files.size(path) < 1
                    || Files.size(path) > maxBytes) {
                throw input
                    ? new OperationException(
                        "PDFA_INPUT_LIMIT_EXCEEDED",
                        "The PDF exceeds the configured PDF/A input limit"
                    )
                    : invalidOutput(null);
            }
        } catch (OperationException exception) {
            throw exception;
        } catch (IOException exception) {
            throw input
                ? invalidPdf(exception)
                : invalidOutput(exception);
        }
    }

    private OperationException encryptedPdf(Throwable cause) {
        return new OperationException(
            "ENCRYPTED_PDF_NOT_SUPPORTED",
            "PDF to PDF/A requires an unencrypted PDF",
            cause
        );
    }

    private OperationException invalidPdf(Throwable cause) {
        return new OperationException(
            "INVALID_PDF",
            "The input is not a readable PDF",
            cause
        );
    }

    private OperationException invalidOutput(Throwable cause) {
        return new OperationException(
            "INVALID_PDFA_OUTPUT",
            "The PDF/A converter returned an invalid PDF",
            cause
        );
    }
}
