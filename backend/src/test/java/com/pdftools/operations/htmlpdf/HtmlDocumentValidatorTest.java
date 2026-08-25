package com.pdftools.operations.htmlpdf;

import com.pdftools.operations.OperationException;
import com.pdftools.operations.OperationSubmission;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HtmlDocumentValidatorTest {

    @TempDir
    Path temporaryDirectory;

    private final HtmlProperties properties = new HtmlProperties();
    private final HtmlDocumentValidator validator =
        new HtmlDocumentValidator(properties);

    @Test
    void acceptsOneUtf8HtmlDocument() throws Exception {
        validator.validateSubmission(new OperationSubmission(
            new tools.jackson.databind.ObjectMapper().readTree("{}"),
            List.of(descriptor("page.html", "text/html", 20))
        ));
        Path source = temporaryDirectory.resolve("page.html");
        Files.writeString(source, "\uFEFF<!doctype html><p>Hello</p>");

        validator.validate(source, () -> {
        });
    }

    @Test
    void rejectsInvalidUtf8AndNullCharacters() throws Exception {
        Path invalidUtf8 = temporaryDirectory.resolve("invalid.html");
        Files.write(invalidUtf8, new byte[]{
            '<',
            'p',
            '>',
            (byte) 0xC3,
            0x28
        });
        assertValidationCode("INVALID_HTML_ENCODING", invalidUtf8);

        Path nullCharacter = temporaryDirectory.resolve("null.html");
        Files.writeString(nullCharacter, "<p>before\u0000after</p>");
        assertValidationCode("INVALID_HTML_DOCUMENT", nullCharacter);
    }

    @Test
    void rejectsWrongFileCountTypeAndSize() throws Exception {
        assertSubmissionCode(
            "INVALID_FILE_COUNT",
            List.of(
                descriptor("one.html", "text/html", 10),
                descriptor("two.html", "text/html", 10)
            )
        );
        assertSubmissionCode(
            "INVALID_HTML_FILE",
            List.of(descriptor("page.txt", "text/plain", 10))
        );
        assertSubmissionCode(
            "HTML_INPUT_SIZE_LIMIT_EXCEEDED",
            List.of(descriptor(
                "page.html",
                "text/html",
                properties.getMaxInputBytes() + 1
            ))
        );
    }

    private OperationSubmission.UploadDescriptor descriptor(
            String filename,
            String mediaType,
            long size) {
        return new OperationSubmission.UploadDescriptor(
            1,
            filename,
            mediaType,
            size
        );
    }

    private void assertSubmissionCode(
            String code,
            List<OperationSubmission.UploadDescriptor> files)
            throws Exception {
        OperationException exception = assertThrows(
            OperationException.class,
            () -> validator.validateSubmission(new OperationSubmission(
                new tools.jackson.databind.ObjectMapper().readTree("{}"),
                files
            ))
        );
        assertEquals(code, exception.getCode());
    }

    private void assertValidationCode(String code, Path source) {
        OperationException exception = assertThrows(
            OperationException.class,
            () -> validator.validate(source, () -> {
            })
        );
        assertEquals(code, exception.getCode());
    }
}
