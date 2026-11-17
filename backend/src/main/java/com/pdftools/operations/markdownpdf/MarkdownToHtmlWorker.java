package com.pdftools.operations.markdownpdf;

import com.pdftools.operations.OperationException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

final class MarkdownToHtmlWorker {

    void convert(MarkdownToHtmlRequest request) {
        MarkdownToHtmlRequest.Limits limits = request.limits();
        String html;
        if (request.kind() == MarkdownSourceKind.BUNDLE) {
            try (MarkdownBundle bundle = MarkdownBundle.open(
                    request.source(),
                    limits.maxBundleEntries())) {
                String markdown = decode(
                    bundle.readMarkdown(limits.maxMarkdownBytes())
                );
                html = render(
                    markdown,
                    bundle,
                    bundle.markdownDirectory(),
                    limits
                );
            } catch (IOException exception) {
                throw new OperationException(
                    "INVALID_MARKDOWN_BUNDLE",
                    "The ZIP bundle could not be read",
                    exception
                );
            }
        } else {
            html = render(
                decode(readMarkdown(request.source(), limits)),
                null,
                "",
                limits
            );
        }
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > limits.maxHtmlBytes()) {
            throw MarkdownToHtmlRenderer.renderedTooLarge();
        }
        try {
            Files.write(
                request.output(),
                bytes,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE
            );
        } catch (IOException exception) {
            throw new OperationException(
                "MARKDOWN_OUTPUT_WRITE_FAILED",
                "The rendered HTML could not be written",
                exception
            );
        }
    }

    private String render(
            String markdown,
            MarkdownImageEmbedder.ImageSource images,
            String baseDirectory,
            MarkdownToHtmlRequest.Limits limits) {
        return new MarkdownToHtmlRenderer(new MarkdownImageEmbedder(
            images,
            baseDirectory,
            limits.maxImages(),
            limits.maxTotalImageBytes(),
            limits.maxHtmlBytes()
        )).render(markdown);
    }

    private byte[] readMarkdown(
            Path source,
            MarkdownToHtmlRequest.Limits limits) {
        try {
            if (Files.isSymbolicLink(source)
                    || !Files.isRegularFile(
                        source,
                        LinkOption.NOFOLLOW_LINKS)) {
                throw invalidDocument(null);
            }
            if (Files.size(source) > limits.maxMarkdownBytes()) {
                throw new OperationException(
                    "MARKDOWN_INPUT_SIZE_LIMIT_EXCEEDED",
                    "The Markdown document exceeds the configured input limit"
                );
            }
            return Files.readAllBytes(source);
        } catch (IOException exception) {
            throw invalidDocument(exception);
        }
    }

    static String decode(byte[] bytes) {
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString();
        } catch (CharacterCodingException exception) {
            throw new OperationException(
                "INVALID_MARKDOWN_ENCODING",
                "The Markdown document must use valid UTF-8",
                exception
            );
        }
        if (text.indexOf('\u0000') >= 0) {
            throw invalidDocument(null);
        }
        if (!text.isEmpty() && text.charAt(0) == '\uFEFF') {
            text = text.substring(1);
        }
        if (text.isBlank()) {
            throw new OperationException(
                "EMPTY_MARKDOWN_DOCUMENT",
                "The Markdown document has no content"
            );
        }
        return text;
    }

    private static OperationException invalidDocument(Throwable cause) {
        return new OperationException(
            "INVALID_MARKDOWN_DOCUMENT",
            "The file is not a readable Markdown document",
            cause
        );
    }
}
