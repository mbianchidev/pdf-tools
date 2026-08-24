package com.pdftools.operations.shared.worker;

import com.pdftools.operations.OperationCancelledException;
import com.pdftools.operations.OperationException;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public final class IsolatedJavaWorker {

    private static final long MIN_HEAP_BYTES = 32L * 1024L * 1024L;
    private static final Duration MAX_TIMEOUT = Duration.ofHours(1);

    private IsolatedJavaWorker() {
    }

    public static int run(
            Spec spec,
            List<String> arguments,
            Runnable cancellationCheck,
            Runnable poll) {
        Process worker = start(spec, arguments);
        long started = System.nanoTime();
        try {
            while (!worker.waitFor(100, TimeUnit.MILLISECONDS)) {
                cancellationCheck.run();
                if (System.nanoTime() - started
                        >= spec.timeout().toNanos()) {
                    throw new OperationException(
                        spec.timeoutCode(),
                        spec.timeoutMessage()
                    );
                }
                poll.run();
            }
            cancellationCheck.run();
            poll.run();
            return worker.exitValue();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            terminate(worker);
            throw new OperationCancelledException();
        } catch (RuntimeException exception) {
            terminate(worker);
            throw exception;
        }
    }

    public static OperationException readFailure(
            int exitCode,
            Path errorFile,
            String fallbackCode,
            String fallbackMessage,
            Logger logger) {
        if (exitCode == 2
                && Files.isRegularFile(
                    errorFile,
                    LinkOption.NOFOLLOW_LINKS
                )) {
            try {
                List<String> lines = Files.readAllLines(errorFile);
                if (lines.size() >= 2
                        && lines.getFirst().matches("[A-Z0-9_]{1,96}")) {
                    return new OperationException(
                        lines.getFirst(),
                        lines.get(1)
                    );
                }
            } catch (IOException exception) {
                logger.warn(
                    "Could not read isolated worker error {}",
                    errorFile,
                    exception
                );
            }
        }
        return new OperationException(fallbackCode, fallbackMessage);
    }

    public static void writeError(
            Path errorFile,
            String code,
            String message) {
        try {
            Files.writeString(errorFile, code + "\n" + message);
        } catch (IOException exception) {
            exception.printStackTrace(System.err);
        }
    }

    public static void terminate(Process worker) {
        if (!worker.isAlive()) {
            return;
        }
        worker.destroy();
        try {
            if (!worker.waitFor(2, TimeUnit.SECONDS)) {
                worker.destroyForcibly();
                worker.waitFor(2, TimeUnit.SECONDS);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            worker.destroyForcibly();
        }
    }

    private static Process start(Spec spec, List<String> arguments) {
        ProcessBuilder builder = new ProcessBuilder(command(spec, arguments))
            .redirectOutput(ProcessBuilder.Redirect.INHERIT)
            .redirectError(ProcessBuilder.Redirect.INHERIT);
        builder.environment().remove("JAVA_TOOL_OPTIONS");
        builder.environment().remove("_JAVA_OPTIONS");
        builder.environment().remove("JDK_JAVA_OPTIONS");
        builder.environment().remove("CLASSPATH");
        try {
            return builder.start();
        } catch (IOException exception) {
            throw new OperationException(
                spec.startCode(),
                spec.startMessage(),
                exception
            );
        }
    }

    private static List<String> command(
            Spec spec,
            List<String> arguments) {
        String classpath = System.getProperty(
            "surefire.test.class.path",
            System.getProperty("java.class.path")
        );
        List<String> command = new ArrayList<>();
        command.add(Path.of(
            System.getProperty("java.home"),
            "bin",
            "java"
        ).toString());
        command.add("-Xmx" + spec.maxHeapBytes());
        command.add("-XX:+ExitOnOutOfMemoryError");
        command.add("-Djava.awt.headless=true");
        command.add("-cp");
        command.add(classpath);
        if (isBootJarClasspath(classpath)) {
            command.add("-Dloader.main=" + spec.mainClass().getName());
            command.add(
                "org.springframework.boot.loader.launch.PropertiesLauncher"
            );
        } else {
            command.add(spec.mainClass().getName());
        }
        command.addAll(arguments);
        return List.copyOf(command);
    }

    private static boolean isBootJarClasspath(String classpath) {
        return !classpath.contains(File.pathSeparator)
            && classpath.toLowerCase(Locale.ROOT).endsWith(".jar");
    }

    public record Spec(
        Class<?> mainClass,
        long maxHeapBytes,
        Duration timeout,
        String startCode,
        String startMessage,
        String timeoutCode,
        String timeoutMessage
    ) {
        public Spec {
            Objects.requireNonNull(mainClass);
            Objects.requireNonNull(timeout);
            Objects.requireNonNull(startCode);
            Objects.requireNonNull(startMessage);
            Objects.requireNonNull(timeoutCode);
            Objects.requireNonNull(timeoutMessage);
            if (maxHeapBytes < MIN_HEAP_BYTES
                    || timeout.isZero()
                    || timeout.isNegative()
                    || timeout.compareTo(MAX_TIMEOUT) > 0) {
                throw new IllegalStateException(
                    "Isolated worker limits are invalid"
                );
            }
        }
    }
}
