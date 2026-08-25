package com.pdftools.operations.htmlpdf;

import com.pdftools.operations.OperationCancelledException;
import com.pdftools.operations.OperationException;
import com.pdftools.operations.OperationSubmission;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class HtmlDocumentValidator {

    private static final Set<String> MEDIA_TYPES = Set.of(
        "text/html",
        "application/xhtml+xml",
        "application/octet-stream"
    );

    private final HtmlProperties properties;

    public HtmlDocumentValidator(HtmlProperties properties) {
        this.properties = properties;
    }

    public void validateSubmission(OperationSubmission submission) {
        if (submission.files().size() != 1) {
            throw new OperationException(
                "INVALID_FILE_COUNT",
                "HTML to PDF requires exactly one HTML document"
            );
        }
        OperationSubmission.UploadDescriptor file =
            submission.files().getFirst();
        String filename = file.filename().toLowerCase(Locale.ROOT);
        if ((!filename.endsWith(".html") && !filename.endsWith(".htm"))
                || !MEDIA_TYPES.contains(
                    file.mediaType().toLowerCase(Locale.ROOT))) {
            throw new OperationException(
                "INVALID_HTML_FILE",
                "HTML to PDF accepts HTML and HTM files"
            );
        }
        if (file.sizeBytes() < 1
                || file.sizeBytes() > properties.getMaxInputBytes()) {
            throw new OperationException(
                "HTML_INPUT_SIZE_LIMIT_EXCEEDED",
                "The HTML document exceeds the configured input limit"
            );
        }
    }

    public void validate(Path source, Runnable cancellationCheck) {
        if (Files.isSymbolicLink(source)
                || !Files.isRegularFile(
                    source,
                    LinkOption.NOFOLLOW_LINKS)) {
            throw invalidDocument(null);
        }
        var decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT);
        char[] buffer = new char[8192];
        try (Reader reader = new InputStreamReader(
                Files.newInputStream(source),
                decoder)) {
            int read;
            while ((read = reader.read(buffer)) != -1) {
                cancellationCheck.run();
                for (int index = 0; index < read; index++) {
                    if (buffer[index] == '\u0000') {
                        throw invalidDocument(null);
                    }
                }
            }
        } catch (OperationException | OperationCancelledException exception) {
            throw exception;
        } catch (java.nio.charset.CharacterCodingException exception) {
            throw new OperationException(
                "INVALID_HTML_ENCODING",
                "The HTML document must use valid UTF-8",
                exception
            );
        } catch (IOException exception) {
            if (causedByCodingError(exception)) {
                throw new OperationException(
                    "INVALID_HTML_ENCODING",
                    "The HTML document must use valid UTF-8",
                    exception
                );
            }
            throw invalidDocument(exception);
        }
    }

    private boolean causedByCodingError(Throwable exception) {
        return List.of(
            java.nio.charset.MalformedInputException.class,
            java.nio.charset.UnmappableCharacterException.class
        ).stream().anyMatch(type -> type.isInstance(exception)
            || type.isInstance(exception.getCause()));
    }

    private OperationException invalidDocument(Throwable cause) {
        return new OperationException(
            "INVALID_HTML_DOCUMENT",
            "The file is not a readable HTML document",
            cause
        );
    }
}
