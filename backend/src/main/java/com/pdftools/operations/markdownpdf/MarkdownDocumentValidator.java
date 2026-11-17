package com.pdftools.operations.markdownpdf;

import com.pdftools.operations.OperationException;
import com.pdftools.operations.OperationSubmission;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

@Component
public class MarkdownDocumentValidator {

    private static final Set<String> MARKDOWN_MEDIA_TYPES = Set.of(
        "text/markdown",
        "text/x-markdown",
        "text/plain",
        "application/octet-stream"
    );
    private static final Set<String> BUNDLE_MEDIA_TYPES = Set.of(
        "application/zip",
        "application/x-zip-compressed",
        "application/octet-stream"
    );

    private final MarkdownProperties properties;

    public MarkdownDocumentValidator(MarkdownProperties properties) {
        this.properties = properties;
    }

    public void validateSubmission(OperationSubmission submission) {
        if (submission.files().size() != 1) {
            throw new OperationException(
                "INVALID_FILE_COUNT",
                "Markdown to PDF requires exactly one Markdown file or bundle"
            );
        }
        OperationSubmission.UploadDescriptor file =
            submission.files().getFirst();
        MarkdownSourceKind kind = MarkdownSourceKind.fromFilename(
            file.filename()
        );
        String mediaType = baseMediaType(file.mediaType());
        Set<String> allowed = kind == MarkdownSourceKind.BUNDLE
            ? BUNDLE_MEDIA_TYPES
            : MARKDOWN_MEDIA_TYPES;
        if (!allowed.contains(mediaType)) {
            throw MarkdownSourceKind.invalidFile();
        }
        long limit = kind == MarkdownSourceKind.BUNDLE
            ? properties.getMaxBundleBytes()
            : properties.getMaxInputBytes();
        if (file.sizeBytes() < 1 || file.sizeBytes() > limit) {
            throw new OperationException(
                "MARKDOWN_INPUT_SIZE_LIMIT_EXCEEDED",
                kind == MarkdownSourceKind.BUNDLE
                    ? "The Markdown bundle exceeds the configured input limit"
                    : "The Markdown document exceeds the configured input limit"
            );
        }
    }

    private String baseMediaType(String mediaType) {
        if (mediaType == null) {
            return "application/octet-stream";
        }
        int separator = mediaType.indexOf(';');
        String base = separator < 0
            ? mediaType
            : mediaType.substring(0, separator);
        return base.trim().toLowerCase(Locale.ROOT);
    }
}
