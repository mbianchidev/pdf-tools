package com.pdftools.operations.office;

import com.pdftools.operations.BoundedOutputStream;
import com.pdftools.operations.OperationCancelledException;
import com.pdftools.operations.OperationException;
import com.pdftools.operations.OperationInput;
import com.pdftools.operations.OutputLimitExceededException;
import com.pdftools.operations.shared.pdf.PdfInputValidator;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntConsumer;
import java.util.stream.Stream;

@Component
public class OfficeConversionQueueClient {

    private static final Logger logger =
        LoggerFactory.getLogger(OfficeConversionQueueClient.class);
    private static final Duration CONVERSION_GRACE =
        Duration.ofSeconds(30);
    private static final Duration CANCELLATION_GRACE =
        Duration.ofSeconds(5);
    private static final Duration ACKNOWLEDGEMENT_GRACE =
        Duration.ofSeconds(2);

    private final OfficeConversionProperties properties;

    public OfficeConversionQueueClient(
            OfficeConversionProperties properties) {
        this.properties = properties;
    }

    public Path convertWord(
            OperationInput input,
            Path workspace,
            IntConsumer progress,
            Runnable cancellationCheck) {
        return convert(
            input,
            workspace,
            OfficeDocumentType.WORD,
            progress,
            cancellationCheck
        );
    }

    public Path convertPowerPoint(
            OperationInput input,
            Path workspace,
            IntConsumer progress,
            Runnable cancellationCheck) {
        return convert(
            input,
            workspace,
            OfficeDocumentType.POWERPOINT,
            progress,
            cancellationCheck
        );
    }

    private Path convert(
            OperationInput input,
            Path workspace,
            OfficeDocumentType documentType,
            IntConsumer progress,
            Runnable cancellationCheck) {
        Roots roots = requireRoots();
        cleanupStale(roots);
        String requestId = UUID.randomUUID().toString();
        Path request = roots.requests().resolve(requestId);
        boolean published = false;
        try {
            Files.createDirectory(request);
            String extension = documentType.extension(
                input.originalFilename()
            );
            Files.copy(
                input.path(),
                request.resolve(OfficeQueueProtocol.INPUT + extension)
            );
            OfficeQueueProtocol.writeRequest(
                request.resolve(OfficeQueueProtocol.REQUEST),
                new OfficeQueueProtocol.Request(
                    documentType.key(),
                    extension
                )
            );
            OfficeQueueProtocol.marker(
                request.resolve(OfficeQueueProtocol.READY)
            );
            published = true;
            progress.accept(3);
            return await(
                roots,
                requestId,
                request,
                workspace.resolve(documentType.outputFilename()),
                documentType,
                progress,
                cancellationCheck
            );
        } catch (OperationCancelledException exception) {
            if (published) {
                cancelAndAwait(roots, requestId, request);
            } else {
                cleanup(request);
            }
            throw exception;
        } catch (OperationException exception) {
            if (!published) {
                cleanup(request);
            }
            throw exception;
        } catch (IOException exception) {
            cleanup(request);
            throw new OperationException(
                "OFFICE_QUEUE_WRITE_FAILED",
                "The " + documentType.label()
                    + " could not be queued for conversion",
                exception
            );
        }
    }

    private Path await(
            Roots roots,
            String requestId,
            Path request,
            Path destination,
            OfficeDocumentType documentType,
            IntConsumer progress,
            Runnable cancellationCheck) {
        long queuedAt = System.nanoTime();
        long queueDeadline = queuedAt
            + properties.getQueueWaitTimeout().toNanos();
        long conversionDeadline = 0;
        int reported = 3;
        Path response = roots.responses().resolve(requestId);
        while (true) {
            cancellationCheck.run();
            boolean started = Files.isDirectory(
                response,
                LinkOption.NOFOLLOW_LINKS
            );
            if (started && conversionDeadline == 0) {
                conversionDeadline = System.nanoTime()
                    + properties.getWallTimeout()
                        .plus(CONVERSION_GRACE)
                        .toNanos();
            }
            Path completed = response.resolve(OfficeQueueProtocol.COMPLETED);
            Path failed = response.resolve(OfficeQueueProtocol.FAILED);
            if (Files.isRegularFile(completed, LinkOption.NOFOLLOW_LINKS)) {
                copyOutput(
                    response.resolve(OfficeQueueProtocol.OUTPUT),
                    destination,
                    cancellationCheck,
                    documentType
                );
                validatePdf(destination, documentType);
                acknowledge(roots, requestId, request, response);
                progress.accept(97);
                return destination;
            }
            if (Files.isRegularFile(failed, LinkOption.NOFOLLOW_LINKS)) {
                OfficeQueueProtocol.Failure failure =
                    OfficeQueueProtocol.readFailure(failed);
                acknowledge(roots, requestId, request, response);
                throw new OperationException(
                    failure.code(),
                    failure.message()
                );
            }
            Path progressFile = response.resolve(
                OfficeQueueProtocol.PROGRESS
            );
            if (Files.isRegularFile(
                    progressFile,
                    LinkOption.NOFOLLOW_LINKS)) {
                int current = OfficeQueueProtocol.readProgress(progressFile);
                if (current > reported) {
                    reported = current;
                    progress.accept(current);
                }
            }
            long now = System.nanoTime();
            if ((!started && now >= queueDeadline)
                    || (started && now >= conversionDeadline)) {
                abandon(roots, requestId, request);
                throw new OperationException(
                    "OFFICE_QUEUE_TIMEOUT",
                    "The isolated Office converter did not respond in time"
                );
            }
            sleep();
        }
    }

    private void copyOutput(
            Path source,
            Path destination,
            Runnable cancellationCheck,
            OfficeDocumentType documentType) {
        if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) {
            throw invalidOutput(documentType);
        }
        Set<OpenOption> options = Set.of(
            StandardOpenOption.READ,
            LinkOption.NOFOLLOW_LINKS
        );
        try (SeekableByteChannel channel =
                 Files.newByteChannel(source, options);
             InputStream input = Channels.newInputStream(channel);
             OutputStream fileOutput = Files.newOutputStream(
                 destination,
                 StandardOpenOption.CREATE_NEW
             );
             BoundedOutputStream bounded = new BoundedOutputStream(
                 fileOutput,
                 properties.getMaxOutputBytes(),
                 cancellationCheck
             )) {
            input.transferTo(bounded);
        } catch (OutputLimitExceededException exception) {
            deleteDestination(destination);
            throw new OperationException(
                documentType.code("PDF_OUTPUT_LIMIT_EXCEEDED"),
                "The converted PDF exceeds the configured output limit",
                exception
            );
        } catch (IOException exception) {
            deleteDestination(destination);
            throw new OperationException(
                "OFFICE_QUEUE_OUTPUT_FAILED",
                "The converted PDF could not be read safely",
                exception
            );
        }
    }

    private void validatePdf(
            Path output,
            OfficeDocumentType documentType) {
        try {
            PdfInputValidator.requirePdfHeader(output);
            try (PDDocument document = Loader.loadPDF(output.toFile())) {
                if (document.isEncrypted()
                        || document.getNumberOfPages() < 1) {
                    throw invalidOutput(documentType);
                }
            }
        } catch (OperationException exception) {
            deleteDestination(output);
            throw new OperationException(
                documentType.invalidPdfCode(),
                "The isolated converter returned an unreadable PDF",
                exception
            );
        } catch (IOException exception) {
            deleteDestination(output);
            throw new OperationException(
                documentType.invalidPdfCode(),
                "The isolated converter returned an unreadable PDF",
                exception
            );
        }
    }

    private Roots requireRoots() {
        Roots roots = new Roots(
            properties.getQueueRequestRoot().toAbsolutePath(),
            properties.getQueueResponseRoot().toAbsolutePath(),
            properties.getQueueSignalRoot().toAbsolutePath()
        );
        for (Path root : roots.paths()) {
            try {
                Files.createDirectories(root);
                if (Files.isSymbolicLink(root)
                        || !Files.isDirectory(
                            root,
                            LinkOption.NOFOLLOW_LINKS
                        )) {
                    throw queueUnavailable();
                }
            } catch (IOException exception) {
                throw new OperationException(
                    "OFFICE_QUEUE_UNAVAILABLE",
                    "The isolated Office converter queue is unavailable",
                    exception
                );
            }
        }
        return roots;
    }

    private void cancelAndAwait(
            Roots roots,
            String requestId,
            Path request) {
        abandon(roots, requestId, request);
    }

    private void abandon(
            Roots roots,
            String requestId,
            Path request) {
        signal(roots, requestId, OfficeQueueProtocol.CANCEL);
        signal(roots, requestId, OfficeQueueProtocol.ABANDONED);
        try {
            Files.deleteIfExists(
                request.resolve(OfficeQueueProtocol.READY)
            );
        } catch (IOException exception) {
            throw new OperationException(
                "OFFICE_QUEUE_CANCEL_FAILED",
                "The Office request could not be cancelled safely",
                exception
            );
        }
        Path response = roots.responses().resolve(requestId);
        long deadline = System.nanoTime() + CANCELLATION_GRACE.toNanos();
        while (System.nanoTime() < deadline) {
            if (terminal(response)) {
                acknowledge(roots, requestId, request, response);
                return;
            }
            sleep();
        }
        if (!Files.exists(response, LinkOption.NOFOLLOW_LINKS)) {
            cleanup(request);
            deleteSignals(roots, requestId);
        }
    }

    private void acknowledge(
            Roots roots,
            String requestId,
            Path request,
            Path response) {
        signal(roots, requestId, OfficeQueueProtocol.ACKNOWLEDGED);
        cleanup(request);
        long deadline = System.nanoTime()
            + ACKNOWLEDGEMENT_GRACE.toNanos();
        while (Files.exists(response, LinkOption.NOFOLLOW_LINKS)
                && System.nanoTime() < deadline) {
            sleep();
        }
        if (!Files.exists(response, LinkOption.NOFOLLOW_LINKS)) {
            deleteSignals(roots, requestId);
        }
    }

    public void cleanupStale() {
        if (!properties.getMode().equals("queue")) {
            return;
        }
        cleanupStale(requireRoots());
    }

    private void cleanupStale(Roots roots) {
        Instant cutoff = Instant.now().minus(properties.getQueueRetention());
        try (Stream<Path> requests = Files.list(roots.requests())) {
            for (Path request : requests
                    .filter(path -> Files.isDirectory(
                        path,
                        LinkOption.NOFOLLOW_LINKS
                    ))
                    .toList()) {
                if (Files.getLastModifiedTime(request).toInstant()
                        .isBefore(cutoff)) {
                    String requestId = request.getFileName().toString();
                    signal(
                        roots,
                        requestId,
                        OfficeQueueProtocol.ABANDONED
                    );
                    cleanup(request);
                }
            }
        } catch (IOException exception) {
            logger.error("Could not clean stale Office requests", exception);
        }
        cleanupConsumedSignals(roots);
    }

    private void cleanupConsumedSignals(Roots roots) {
        try (Stream<Path> signals = Files.list(roots.signals())) {
            for (Path signal : signals
                    .filter(path -> Files.isRegularFile(
                        path,
                        LinkOption.NOFOLLOW_LINKS
                    ))
                    .toList()) {
                String name = signal.getFileName().toString();
                if (name.length() < 36) {
                    continue;
                }
                String requestId = name.substring(0, 36);
                if (!Files.exists(
                        roots.requests().resolve(requestId),
                        LinkOption.NOFOLLOW_LINKS)
                        && !Files.exists(
                            roots.responses().resolve(requestId),
                            LinkOption.NOFOLLOW_LINKS)) {
                    Files.deleteIfExists(signal);
                }
            }
        } catch (IOException exception) {
            logger.error("Could not clean Office queue signals", exception);
        }
    }

    private void signal(Roots roots, String requestId, String suffix) {
        OfficeQueueProtocol.marker(
            roots.signals().resolve(requestId + suffix)
        );
    }

    private void deleteSignal(
            Roots roots,
            String requestId,
            String suffix) {
        try {
            Files.deleteIfExists(roots.signals().resolve(requestId + suffix));
        } catch (IOException exception) {
            logger.error(
                "Could not remove Office queue signal {}{}",
                requestId,
                suffix,
                exception
            );
        }
    }

    private void deleteSignals(Roots roots, String requestId) {
        deleteSignal(roots, requestId, OfficeQueueProtocol.CANCEL);
        deleteSignal(roots, requestId, OfficeQueueProtocol.ABANDONED);
        deleteSignal(roots, requestId, OfficeQueueProtocol.ACKNOWLEDGED);
    }

    private boolean terminal(Path response) {
        return Files.exists(response.resolve(OfficeQueueProtocol.COMPLETED))
            || Files.exists(response.resolve(OfficeQueueProtocol.FAILED));
    }

    private void cleanup(Path request) {
        if (!Files.exists(request, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(request)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException exception) {
            logger.error(
                "Could not remove Office queue request {}",
                request,
                exception
            );
        }
    }

    private void deleteDestination(Path destination) {
        try {
            Files.deleteIfExists(destination);
        } catch (IOException exception) {
            logger.error(
                "Could not remove partial Office output {}",
                destination,
                exception
            );
        }
    }

    private void sleep() {
        try {
            Thread.sleep(100);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new OperationCancelledException();
        }
    }

    private OperationException queueUnavailable() {
        return new OperationException(
            "OFFICE_QUEUE_UNAVAILABLE",
            "The isolated Office converter queue is unavailable"
        );
    }

    private OperationException invalidOutput(
            OfficeDocumentType documentType) {
        return new OperationException(
            documentType.invalidPdfCode(),
            "The isolated converter returned an unreadable PDF"
        );
    }

    private record Roots(
        Path requests,
        Path responses,
        Path signals
    ) {
        private List<Path> paths() {
            return List.of(requests, responses, signals);
        }
    }
}
