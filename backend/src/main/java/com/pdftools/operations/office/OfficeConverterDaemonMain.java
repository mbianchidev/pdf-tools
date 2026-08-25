package com.pdftools.operations.office;

import com.pdftools.operations.OperationCancelledException;
import com.pdftools.operations.OperationException;
import com.pdftools.operations.OperationInput;
import com.pdftools.operations.wordpdf.LibreOfficeWordConverter;
import com.pdftools.operations.wordpdf.WordDocumentValidator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.stream.Stream;

public final class OfficeConverterDaemonMain {

    private OfficeConverterDaemonMain() {
    }

    public static void main(String[] arguments) {
        OfficeConversionProperties properties = properties();
        Roots roots = roots(properties);
        Path workRoot = properties.getSidecarWorkRoot().toAbsolutePath();
        try {
            for (Path root : roots.paths()) {
                if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)
                        || Files.isSymbolicLink(root)) {
                    throw new IllegalStateException(
                        "Office queue roots are unavailable"
                    );
                }
                Files.createDirectories(workRoot);
                if (System.getProperty("user.name").equals(
                        properties.getWorkerUser())) {
                    throw new IllegalStateException(
                        "Office supervisor and worker must use different users"
                    );
                }
            }
            Files.deleteIfExists(
                roots.responses().resolve(OfficeQueueProtocol.DAEMON_READY)
            );
            recover(roots, properties.getQueueRetention());
            OfficeQueueProtocol.marker(
                roots.responses().resolve(OfficeQueueProtocol.DAEMON_READY)
            );
            WordDocumentValidator validator =
                new WordDocumentValidator(properties);
            LibreOfficeWordConverter converter =
                new LibreOfficeWordConverter(
                    properties,
                    validator,
                    new NativeProcessSandbox()
                );
            run(
                roots,
                converter,
                properties.getQueueRetention(),
                workRoot
            );
        } catch (Throwable throwable) {
            throwable.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private static void run(
            Roots roots,
            LibreOfficeWordConverter converter,
            Duration retention,
            Path workRoot) {
        while (!Thread.currentThread().isInterrupted()) {
            boolean processed = false;
            cleanupTerminalResponses(roots, retention);
            try (Stream<Path> paths = Files.list(roots.requests())) {
                for (Path request : paths
                        .filter(path -> Files.isDirectory(
                            path,
                            LinkOption.NOFOLLOW_LINKS
                        ))
                        .sorted()
                        .toList()) {
                    String requestId = request.getFileName().toString();
                    if (!requestId.matches(
                            "[0-9a-f]{8}-[0-9a-f]{4}-"
                                + "[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
                        continue;
                    }
                    Path response = roots.responses().resolve(requestId);
                    if (claim(
                            request,
                            response,
                            roots.signals(),
                            requestId)) {
                        process(
                            request,
                            response,
                            roots.signals(),
                            workRoot,
                            converter
                        );
                        processed = true;
                    }
                }
            } catch (IOException exception) {
                throw new OperationException(
                    "OFFICE_QUEUE_READ_FAILED",
                    "The Office queue could not be read",
                    exception
                );
            }
            if (!processed) {
                sleep();
            }
        }
    }

    private static boolean claim(
            Path request,
            Path response,
            Path signalRoot,
            String requestId) {
        if (cancelled(signalRoot, requestId)) {
            return false;
        }
        if (!Files.isRegularFile(
                request.resolve(OfficeQueueProtocol.READY),
                LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }
        if (terminal(response)) {
            return false;
        }
        try {
            if (Files.exists(response, LinkOption.NOFOLLOW_LINKS)) {
                cleanupContents(response);
            } else {
                Files.createDirectory(response);
            }
            if (cancelled(signalRoot, requestId)) {
                cleanup(response);
                return false;
            }
            OfficeQueueProtocol.marker(
                response.resolve(OfficeQueueProtocol.RUNNING)
            );
            return true;
        } catch (IOException exception) {
            return false;
        }
    }

    private static void process(
            Path requestDirectory,
            Path responseDirectory,
            Path signalRoot,
            Path workRoot,
            LibreOfficeWordConverter converter) {
        String requestId = requestDirectory.getFileName().toString();
        Path work = workRoot.resolve(requestId);
        try {
            OfficeQueueProtocol.Request request =
                OfficeQueueProtocol.readRequest(
                    requestDirectory.resolve(OfficeQueueProtocol.REQUEST)
                );
            Path input = requestDirectory.resolve(
                OfficeQueueProtocol.INPUT + request.extension()
            );
            if (Files.isSymbolicLink(input)
                    || !Files.isRegularFile(
                        input,
                        LinkOption.NOFOLLOW_LINKS
                    )) {
                throw new OperationException(
                    "OFFICE_QUEUE_PROTOCOL_ERROR",
                    "The Office queue input is invalid"
                );
            }
            cleanup(work);
            Files.createDirectory(work);
            OperationInput operationInput = new OperationInput(
                1,
                input,
                "source" + request.extension(),
                request.extension().equals(".docx")
                    ? "application/vnd.openxmlformats-officedocument."
                        + "wordprocessingml.document"
                    : "application/msword",
                Files.size(input),
                "office-queue"
            );
            Path output = converter.convert(
                operationInput,
                work,
                progress -> OfficeQueueProtocol.writeProgress(
                    responseDirectory.resolve(OfficeQueueProtocol.PROGRESS),
                    progress
                ),
                () -> {
                    if (Files.exists(signal(
                            signalRoot,
                            requestId,
                            OfficeQueueProtocol.CANCEL))) {
                        throw new OperationCancelledException();
                    }
                }
            );
            OfficeQueueProtocol.move(
                output,
                responseDirectory.resolve(OfficeQueueProtocol.OUTPUT)
            );
            OfficeQueueProtocol.marker(
                responseDirectory.resolve(OfficeQueueProtocol.COMPLETED)
            );
        } catch (OperationCancelledException exception) {
            fail(
                responseDirectory,
                "OFFICE_CONVERSION_CANCELLED",
                "Office conversion was cancelled"
            );
        } catch (OperationException exception) {
            fail(
                responseDirectory,
                exception.getCode(),
                exception.getMessage()
            );
        } catch (IOException exception) {
            fail(
                responseDirectory,
                "OFFICE_CONVERSION_FAILED",
                "The isolated Office converter failed"
            );
        } catch (RuntimeException exception) {
            exception.printStackTrace(System.err);
            fail(
                responseDirectory,
                "OFFICE_CONVERSION_FAILED",
                "The isolated Office converter failed"
            );
        } finally {
            cleanup(work);
        }
    }

    private static void recover(Roots roots, Duration retention)
            throws IOException {
        cleanupTerminalResponses(roots, retention);
        try (Stream<Path> responses = Files.list(roots.responses())) {
            for (Path response : responses
                    .filter(path -> Files.isDirectory(
                        path,
                        LinkOption.NOFOLLOW_LINKS
                    ))
                    .toList()) {
                if (!terminal(response)) {
                    cleanupContents(response);
                }
            }
        }
    }

    private static void cleanupTerminalResponses(
            Roots roots,
            Duration retention) {
        Instant cutoff = Instant.now().minus(retention);
        try (Stream<Path> responses = Files.list(roots.responses())) {
            for (Path response : responses
                    .filter(path -> Files.isDirectory(
                        path,
                        LinkOption.NOFOLLOW_LINKS
                    ))
                    .toList()) {
                String requestId = response.getFileName().toString();
                boolean acknowledged = Files.exists(signal(
                    roots.signals(),
                    requestId,
                    OfficeQueueProtocol.ACKNOWLEDGED
                ));
                boolean expired = Files.getLastModifiedTime(response)
                    .toInstant()
                    .isBefore(cutoff);
                if ((acknowledged && terminal(response)) || expired) {
                    cleanup(response);
                }
            }
        } catch (IOException exception) {
            exception.printStackTrace(System.err);
        }
    }

    private static void fail(
            Path response,
            String code,
            String message) {
        OfficeQueueProtocol.writeFailure(
            response.resolve(OfficeQueueProtocol.FAILED),
            code,
            message
        );
    }

    private static boolean terminal(Path response) {
        return Files.isDirectory(response, LinkOption.NOFOLLOW_LINKS)
            && (Files.exists(response.resolve(OfficeQueueProtocol.COMPLETED))
                || Files.exists(response.resolve(OfficeQueueProtocol.FAILED)));
    }

    private static Path signal(
            Path signalRoot,
            String requestId,
            String suffix) {
        return signalRoot.resolve(requestId + suffix);
    }

    private static boolean cancelled(
            Path signalRoot,
            String requestId) {
        return Files.exists(signal(
            signalRoot,
            requestId,
            OfficeQueueProtocol.CANCEL
        )) || Files.exists(signal(
            signalRoot,
            requestId,
            OfficeQueueProtocol.ABANDONED
        ));
    }

    private static Roots roots(OfficeConversionProperties properties) {
        return new Roots(
            properties.getQueueRequestRoot().toAbsolutePath(),
            properties.getQueueResponseRoot().toAbsolutePath(),
            properties.getQueueSignalRoot().toAbsolutePath()
        );
    }

    private static OfficeConversionProperties properties() {
        OfficeConversionProperties properties =
            new OfficeConversionProperties();
        properties.setMode("direct");
        properties.setIsolatedContainer(true);
        properties.setQueueRequestRoot(Path.of(environment(
            "OFFICE_QUEUE_REQUEST_ROOT",
            "/var/lib/pdf-tools-office/requests"
        )));
        properties.setQueueResponseRoot(Path.of(environment(
            "OFFICE_QUEUE_RESPONSE_ROOT",
            "/var/lib/pdf-tools-office/responses"
        )));
        properties.setQueueSignalRoot(Path.of(environment(
            "OFFICE_QUEUE_SIGNAL_ROOT",
            "/var/lib/pdf-tools-office/signals"
        )));
        properties.setQueueRetention(durationEnvironment(
            "OFFICE_QUEUE_RETENTION",
            Duration.ofHours(1)
        ));
        properties.setSidecarWorkRoot(Path.of(environment(
            "OFFICE_SIDECAR_WORK_ROOT",
            "/tmp/office-work"
        )));
        properties.setWorkerUser(environment(
            "OFFICE_WORKER_USER",
            "officeworker"
        ));
        properties.setLibreOfficeBinary(environment(
            "OFFICE_LIBREOFFICE_BINARY",
            "/usr/bin/soffice"
        ));
        properties.setMaxInputBytes(longEnvironment(
            "OFFICE_MAX_INPUT_BYTES",
            properties.getMaxInputBytes()
        ));
        properties.setMaxExpandedInputBytes(longEnvironment(
            "OFFICE_MAX_EXPANDED_INPUT_BYTES",
            properties.getMaxExpandedInputBytes()
        ));
        properties.setMaxArchiveEntries(intEnvironment(
            "OFFICE_MAX_ARCHIVE_ENTRIES",
            properties.getMaxArchiveEntries()
        ));
        properties.setMaxOutputBytes(longEnvironment(
            "OFFICE_MAX_OUTPUT_BYTES",
            properties.getMaxOutputBytes()
        ));
        properties.setMaxLogBytes(longEnvironment(
            "OFFICE_MAX_LOG_BYTES",
            properties.getMaxLogBytes()
        ));
        properties.setMaxAddressSpaceBytes(longEnvironment(
            "OFFICE_MAX_ADDRESS_SPACE_BYTES",
            properties.getMaxAddressSpaceBytes()
        ));
        properties.setCpuTimeSeconds(longEnvironment(
            "OFFICE_CPU_TIME_SECONDS",
            properties.getCpuTimeSeconds()
        ));
        properties.setMaxOpenFiles(intEnvironment(
            "OFFICE_MAX_OPEN_FILES",
            properties.getMaxOpenFiles()
        ));
        properties.setMaxWorkerProcesses(intEnvironment(
            "OFFICE_MAX_WORKER_PROCESSES",
            properties.getMaxWorkerProcesses()
        ));
        properties.setWallTimeout(durationEnvironment(
            "OFFICE_WALL_TIMEOUT",
            Duration.ofMinutes(2)
        ));
        return properties;
    }

    private static String environment(String name, String fallback) {
        return System.getenv().getOrDefault(name, fallback);
    }

    private static long longEnvironment(String name, long fallback) {
        return Long.parseLong(environment(name, Long.toString(fallback)));
    }

    private static int intEnvironment(String name, int fallback) {
        return Integer.parseInt(environment(name, Integer.toString(fallback)));
    }

    private static Duration durationEnvironment(
            String name,
            Duration fallback) {
        String value = environment(name, fallback.toString()).trim();
        if (value.startsWith("P")) {
            return Duration.parse(value);
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern
            .compile("^(\\d+)(ms|s|m|h)$")
            .matcher(value);
        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                name + " must be an ISO-8601 or short duration"
            );
        }
        long amount = Long.parseLong(matcher.group(1));
        return switch (matcher.group(2)) {
            case "ms" -> Duration.ofMillis(amount);
            case "s" -> Duration.ofSeconds(amount);
            case "m" -> Duration.ofMinutes(amount);
            case "h" -> Duration.ofHours(amount);
            default -> throw new IllegalStateException();
        };
    }

    private static void cleanupContents(Path directory) {
        try (Stream<Path> paths = Files.walk(directory)) {
            for (Path path : paths
                    .filter(candidate -> !candidate.equals(directory))
                    .sorted(Comparator.reverseOrder())
                    .toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException exception) {
            throw new OperationException(
                "OFFICE_QUEUE_CLEANUP_FAILED",
                "A stale Office response could not be reset",
                exception
            );
        }
    }

    private static void cleanup(Path directory) {
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(directory)) {
            for (Path path : paths
                    .sorted(Comparator.reverseOrder())
                    .toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException exception) {
            exception.printStackTrace(System.err);
        }
    }

    private static void sleep() {
        try {
            Thread.sleep(100);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private record Roots(
        Path requests,
        Path responses,
        Path signals
    ) {
        private java.util.List<Path> paths() {
            return java.util.List.of(requests, responses, signals);
        }
    }
}
