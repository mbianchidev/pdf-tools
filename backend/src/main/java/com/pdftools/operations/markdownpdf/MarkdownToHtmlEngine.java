package com.pdftools.operations.markdownpdf;

import com.pdftools.operations.OperationException;
import com.pdftools.operations.OperationInput;
import com.pdftools.operations.htmlpdf.HtmlProperties;
import com.pdftools.operations.shared.worker.IsolatedJavaWorker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;

@Component
public class MarkdownToHtmlEngine {

    private static final Logger logger =
        LoggerFactory.getLogger(MarkdownToHtmlEngine.class);

    private final MarkdownProperties properties;
    private final HtmlProperties htmlProperties;

    public MarkdownToHtmlEngine(
            MarkdownProperties properties,
            HtmlProperties htmlProperties) {
        this.properties = properties;
        this.htmlProperties = htmlProperties;
    }

    public Path render(
            OperationInput input,
            Path workspace,
            Runnable cancellationCheck) {
        MarkdownSourceKind kind = MarkdownSourceKind.fromFilename(
            input.originalFilename()
        );
        requireSource(input.path(), kind);
        Path output = workspace.resolve("markdown-to-pdf.html");
        Path request = workspace.resolve(".markdown-request.bin");
        Path errorFile = workspace.resolve(".markdown-error");
        MarkdownToHtmlRequest.write(request, new MarkdownToHtmlRequest(
            input.path(),
            kind,
            output,
            workspace,
            MarkdownToHtmlRequest.Limits.from(
                properties,
                htmlProperties.getMaxInputBytes()
            )
        ));
        int exitCode;
        try {
            exitCode = IsolatedJavaWorker.run(
                workerSpec(),
                List.of(
                    request.toAbsolutePath().toString(),
                    errorFile.toAbsolutePath().toString()
                ),
                cancellationCheck,
                () -> {
                }
            );
        } catch (RuntimeException exception) {
            deleteOutput(output, exception);
            throw exception;
        }
        if (exitCode != 0) {
            RuntimeException failure = IsolatedJavaWorker.readFailure(
                exitCode,
                errorFile,
                "MARKDOWN_RESOURCE_LIMIT_EXCEEDED",
                "The isolated Markdown worker exited early",
                logger
            );
            deleteOutput(output, failure);
            throw failure;
        }
        requireOutput(output);
        return output;
    }

    private void requireSource(Path source, MarkdownSourceKind kind) {
        long limit = kind == MarkdownSourceKind.BUNDLE
            ? properties.getMaxBundleBytes()
            : properties.getMaxInputBytes();
        try {
            if (Files.isSymbolicLink(source)
                    || !Files.isRegularFile(
                        source,
                        LinkOption.NOFOLLOW_LINKS)) {
                throw new OperationException(
                    "INVALID_MARKDOWN_DOCUMENT",
                    "The file is not a readable Markdown document"
                );
            }
            long size = Files.size(source);
            if (size < 1 || size > limit) {
                throw new OperationException(
                    "MARKDOWN_INPUT_SIZE_LIMIT_EXCEEDED",
                    "The Markdown input exceeds the configured input limit"
                );
            }
        } catch (IOException exception) {
            throw new OperationException(
                "INVALID_MARKDOWN_DOCUMENT",
                "The file is not a readable Markdown document",
                exception
            );
        }
    }

    private void requireOutput(Path output) {
        try {
            if (Files.isSymbolicLink(output)
                    || !Files.isRegularFile(
                        output,
                        LinkOption.NOFOLLOW_LINKS)
                    || Files.size(output) < 1
                    || Files.size(output) > htmlProperties.getMaxInputBytes()) {
                throw invalidOutput(null);
            }
        } catch (IOException exception) {
            OperationException failure = invalidOutput(exception);
            deleteOutput(output, failure);
            throw failure;
        } catch (OperationException exception) {
            deleteOutput(output, exception);
            throw exception;
        }
    }

    private IsolatedJavaWorker.Spec workerSpec() {
        return new IsolatedJavaWorker.Spec(
            MarkdownToHtmlWorkerMain.class,
            properties.getWorkerHeapBytes(),
            properties.getWorkerTimeout(),
            "MARKDOWN_WORKER_START_FAILED",
            "The isolated Markdown worker could not be started",
            "MARKDOWN_TIMEOUT",
            "Markdown rendering exceeded its time limit"
        );
    }

    private void deleteOutput(Path output, RuntimeException failure) {
        try {
            Files.deleteIfExists(output);
        } catch (IOException exception) {
            failure.addSuppressed(exception);
            logger.error(
                "Could not remove partial Markdown HTML output {}",
                output,
                exception
            );
        }
    }

    private OperationException invalidOutput(Throwable cause) {
        return new OperationException(
            "INVALID_MARKDOWN_HTML_OUTPUT",
            "The rendered Markdown HTML is invalid",
            cause
        );
    }
}
