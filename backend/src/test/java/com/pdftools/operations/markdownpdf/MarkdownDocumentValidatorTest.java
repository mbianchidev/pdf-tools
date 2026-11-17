package com.pdftools.operations.markdownpdf;

import com.pdftools.operations.OperationException;
import com.pdftools.operations.OperationSubmission;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MarkdownDocumentValidatorTest {

    private final MarkdownProperties properties = new MarkdownProperties();
    private final MarkdownDocumentValidator validator =
        new MarkdownDocumentValidator(properties);

    @Test
    void acceptsMarkdownFilesAndZipBundles() {
        validator.validateSubmission(submission(
            descriptor("notes.md", "text/markdown; charset=UTF-8", 20)
        ));
        validator.validateSubmission(submission(
            descriptor("README.markdown", "application/octet-stream", 20)
        ));
        validator.validateSubmission(submission(
            descriptor("document-markdown.zip", "application/zip", 20)
        ));
    }

    @Test
    void rejectsWrongCountTypeAndSize() {
        assertCode(
            "INVALID_FILE_COUNT",
            new OperationSubmission(options(), List.of(
                descriptor("a.md", "text/markdown", 10),
                descriptor("b.md", "text/markdown", 10)
            ))
        );
        assertCode(
            "INVALID_MARKDOWN_FILE",
            submission(descriptor("notes.txt", "text/plain", 10))
        );
        assertCode(
            "INVALID_MARKDOWN_FILE",
            submission(descriptor("notes.md", "text/html", 10))
        );
        assertCode(
            "MARKDOWN_INPUT_SIZE_LIMIT_EXCEEDED",
            submission(descriptor(
                "notes.md",
                "text/markdown",
                properties.getMaxInputBytes() + 1
            ))
        );
        assertCode(
            "MARKDOWN_INPUT_SIZE_LIMIT_EXCEEDED",
            submission(descriptor(
                "bundle.zip",
                "application/zip",
                properties.getMaxBundleBytes() + 1
            ))
        );
    }

    private void assertCode(String code, OperationSubmission submission) {
        OperationException failure = assertThrows(
            OperationException.class,
            () -> validator.validateSubmission(submission)
        );
        assertEquals(code, failure.getCode());
    }

    private OperationSubmission submission(
            OperationSubmission.UploadDescriptor descriptor) {
        return new OperationSubmission(options(), List.of(descriptor));
    }

    private tools.jackson.databind.JsonNode options() {
        return new ObjectMapper().readTree("{}");
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
}
