package com.pdftools.operations.wordpdf;

import com.pdftools.operations.OperationCancelledException;
import com.pdftools.operations.OperationException;
import com.pdftools.operations.OperationInput;
import com.pdftools.operations.BoundedOutputStream;
import com.pdftools.operations.OutputLimitExceededException;
import com.pdftools.operations.office.NativeProcessSandbox;
import com.pdftools.operations.office.OfficeConversionProperties;
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
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;

@Component
public class LibreOfficeWordConverter {

    private static final Logger logger =
        LoggerFactory.getLogger(LibreOfficeWordConverter.class);
    private static final String PROFILE_CONFIGURATION = """
        <?xml version="1.0" encoding="UTF-8"?>
        <oor:items xmlns:oor="http://openoffice.org/2001/registry">
          <item oor:path="/org.openoffice.Office.Security/Scripting">
            <prop oor:name="MacroSecurityLevel" oor:op="fuse">
              <value>3</value>
            </prop>
          </item>
          <item oor:path="/org.openoffice.Office.Common/Misc">
            <prop oor:name="FirstRun" oor:op="fuse">
              <value>false</value>
            </prop>
            <prop oor:name="ShowTipOfTheDay" oor:op="fuse">
              <value>false</value>
            </prop>
          </item>
        </oor:items>
        """;
    private static final Duration PROCESS_SHUTDOWN_GRACE =
        Duration.ofSeconds(5);

    private final OfficeConversionProperties properties;
    private final WordDocumentValidator validator;
    private final NativeProcessSandbox sandbox;

    public LibreOfficeWordConverter(
            OfficeConversionProperties properties,
            WordDocumentValidator validator,
            NativeProcessSandbox sandbox) {
        this.properties = properties;
        this.validator = validator;
        this.sandbox = sandbox;
    }

    public Path convert(
            OperationInput input,
            Path workspace,
            IntConsumer progress,
            Runnable cancellationCheck) {
        validator.validate(
            input.path(),
            input.originalFilename(),
            cancellationCheck
        );
        cancellationCheck.run();
        progress.accept(3);

        Path converterRoot = workspace.resolve("word-converter");
        Path outputDirectory = converterRoot.resolve("output");
        Path profile = converterRoot.resolve("profile");
        Path profileUser = profile.resolve("user");
        Path home = converterRoot.resolve("home");
        Path temporary = converterRoot.resolve("tmp");
        Path config = converterRoot.resolve("config");
        Path cache = converterRoot.resolve("cache");
        Path data = converterRoot.resolve("data");
        createDirectories(
            converterRoot,
            outputDirectory,
            profileUser,
            home,
            temporary,
            config,
            cache,
            data
        );
        writeProfile(profileUser);

        String extension = extension(input.originalFilename());
        Path safeInput = converterRoot.resolve("source" + extension);
        copyInput(input.path(), safeInput);
        prepareWorkerOwnership(converterRoot);
        Path generated = outputDirectory.resolve("source.pdf");
        Path finalOutput = workspace.resolve("word-to-pdf.pdf");
        Path stdout = workspace.resolve(".word-converter-stdout.log");
        Path stderr = workspace.resolve(".word-converter-stderr.log");
        Path networkFilter = converterRoot.resolve(".network-filter.bpf");
        Path executable = resolveExecutable();
        List<String> arguments = List.of(
            "-env:UserInstallation=" + profile.toUri().toASCIIString(),
            "--headless",
            "--invisible",
            "--nologo",
            "--nodefault",
            "--nolockcheck",
            "--norestore",
            "--convert-to",
            "pdf:writer_pdf_Export",
            "--outdir",
            outputDirectory.toAbsolutePath().toString(),
            safeInput.toAbsolutePath().toString()
        );
        List<String> command = sandbox.command(
            converterRoot,
            networkFilter,
            executable,
            arguments,
            properties
        );
        Process process = start(
            command,
            converterRoot,
            stdout,
            stderr,
            environment(home, temporary, config, cache, data)
        );
        waitFor(process, progress, cancellationCheck);
        validateLogs(stdout, stderr);
        if (process.exitValue() != 0) {
            logFailure(stderr);
            throw new OperationException(
                "WORD_CONVERSION_FAILED",
                "LibreOffice could not convert the Word document"
            );
        }
        copyOutput(generated, finalOutput, cancellationCheck);
        validateOutput(finalOutput);
        progress.accept(97);
        return finalOutput;
    }

    private Process start(
            List<String> command,
            Path directory,
            Path stdout,
            Path stderr,
            Map<String, String> environment) {
        ProcessBuilder builder = new ProcessBuilder(command)
            .directory(directory.toFile())
            .redirectOutput(stdout.toFile())
            .redirectError(stderr.toFile());
        builder.environment().clear();
        builder.environment().putAll(environment);
        try {
            return builder.start();
        } catch (IOException exception) {
            throw new OperationException(
                "WORD_CONVERTER_START_FAILED",
                "The isolated LibreOffice converter could not be started",
                exception
            );
        }
    }

    private void waitFor(
            Process process,
            IntConsumer progress,
            Runnable cancellationCheck) {
        Duration timeout = properties.getWallTimeout();
        long started = System.nanoTime();
        int reported = 3;
        Set<ProcessHandle> observedDescendants = new HashSet<>();
        try {
            while (!process.waitFor(100, TimeUnit.MILLISECONDS)) {
                observedDescendants.addAll(process.descendants().toList());
                cancellationCheck.run();
                long elapsed = System.nanoTime() - started;
                if (elapsed >= timeout.toNanos()) {
                    throw new OperationException(
                        "WORD_CONVERSION_TIMEOUT",
                        "Word conversion exceeded the configured time limit"
                    );
                }
                int next = Math.min(
                    90,
                    3 + (int) Math.floor(
                        87.0 * elapsed / timeout.toNanos()
                    )
                );
                if (next > reported) {
                    reported = next;
                    progress.accept(next);
                }
            }
            observedDescendants.addAll(process.descendants().toList());
            cancellationCheck.run();
            requireDescendantsExited(observedDescendants);
            if (strictProcessIsolation()) {
                requireProcessGroupExited(process.pid());
            } else {
                terminateResidualProcessGroup(process.pid());
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            terminate(process, observedDescendants);
            throw new OperationCancelledException();
        } catch (RuntimeException exception) {
            terminate(process, observedDescendants);
            throw exception;
        }
    }

    private void requireDescendantsExited(
            Set<ProcessHandle> descendants) {
        long deadline = System.nanoTime()
            + PROCESS_SHUTDOWN_GRACE.toNanos();
        while (descendants.stream().anyMatch(ProcessHandle::isAlive)
                && System.nanoTime() < deadline) {
            try {
                Thread.sleep(20);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new OperationCancelledException();
            }
        }
        if (descendants.stream().anyMatch(ProcessHandle::isAlive)) {
            descendants.forEach(ProcessHandle::destroyForcibly);
            throw new OperationException(
                "WORD_CONVERTER_PROCESS_LEAK",
                "LibreOffice did not shut down cleanly"
            );
        }
    }

    private void terminate(
            Process process,
            Set<ProcessHandle> observedDescendants) {
        OperationException signalFailure = trySignalProcessGroup(
            process.pid(),
            "TERM"
        );
        destroyTree(process, observedDescendants, false);
        process.destroy();
        try {
            process.waitFor(2, TimeUnit.SECONDS);
            destroyTree(process, observedDescendants, true);
            OperationException killFailure = trySignalProcessGroup(
                process.pid(),
                "KILL"
            );
            if (signalFailure == null) {
                signalFailure = killFailure;
            }
            if (process.isAlive()) {
                process.destroyForcibly();
                process.waitFor(2, TimeUnit.SECONDS);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            destroyTree(process, observedDescendants, true);
            OperationException killFailure = trySignalProcessGroup(
                process.pid(),
                "KILL"
            );
            if (signalFailure == null) {
                signalFailure = killFailure;
            }
            process.destroyForcibly();
        }
        if (signalFailure != null) {
            throw signalFailure;
        }
    }

    private OperationException trySignalProcessGroup(
            long processGroupId,
            String signal) {
        try {
            signalProcessGroup(processGroupId, signal);
            return null;
        } catch (OperationException exception) {
            return exception;
        } catch (RuntimeException exception) {
            return new OperationException(
                "WORD_PROCESS_GROUP_UNCHECKABLE",
                "The LibreOffice process group could not be controlled",
                exception
            );
        }
    }

    private void requireProcessGroupExited(long processGroupId) {
        if (!isLinux()) {
            return;
        }
        long deadline = System.nanoTime()
            + PROCESS_SHUTDOWN_GRACE.toNanos();
        while (processGroupAlive(processGroupId)
                && System.nanoTime() < deadline) {
            try {
                Thread.sleep(20);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new OperationCancelledException();
            }
        }
        if (processGroupAlive(processGroupId)) {
            signalProcessGroup(processGroupId, "KILL");
            throw new OperationException(
                "WORD_CONVERTER_PROCESS_LEAK",
                "LibreOffice did not shut down cleanly"
            );
        }
    }

    private boolean processGroupAlive(long processGroupId) {
        return signalProcessGroup(processGroupId, "0");
    }

    private void terminateResidualProcessGroup(long processGroupId) {
        OperationException failure = trySignalProcessGroup(
            processGroupId,
            "KILL"
        );
        if (failure != null) {
            throw failure;
        }
    }

    private boolean signalProcessGroup(
            long processGroupId,
            String signal) {
        if (!isLinux()) {
            return false;
        }
        try {
            ProcessBuilder builder = new ProcessBuilder(
                "/bin/kill",
                "-" + signal,
                "--",
                "-" + processGroupId
            )
                .redirectErrorStream(true);
            builder.environment().put("LC_ALL", "C");
            Process signalProcess = builder.start();
            byte[] message = signalProcess.getInputStream().readNBytes(512);
            if (!signalProcess.waitFor(2, TimeUnit.SECONDS)) {
                signalProcess.destroyForcibly();
                throw processGroupFailure();
            }
            if (signalProcess.exitValue() == 0) {
                return true;
            }
            String error = new String(
                message,
                StandardCharsets.UTF_8
            );
            if (error.contains("No such process")) {
                return false;
            }
            throw processGroupFailure();
        } catch (IOException exception) {
            throw new OperationException(
                "WORD_PROCESS_GROUP_UNCHECKABLE",
                "The LibreOffice process group could not be controlled",
                exception
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new OperationCancelledException();
        }
    }

    private boolean isLinux() {
        return System.getProperty("os.name")
            .toLowerCase(Locale.ROOT)
            .contains("linux");
    }

    private boolean strictProcessIsolation() {
        return isLinux()
            && properties.isIsolatedContainer()
            && !properties.getWorkerUser().equals(
                System.getProperty("user.name")
            );
    }

    private OperationException processGroupFailure() {
        return new OperationException(
            "WORD_PROCESS_GROUP_UNCHECKABLE",
            "The LibreOffice process group could not be controlled"
        );
    }

    private void destroyTree(
            Process process,
            Set<ProcessHandle> observedDescendants,
            boolean forcibly) {
        observedDescendants.addAll(process.descendants().toList());
        List<ProcessHandle> descendants =
            new ArrayList<>(observedDescendants);
        for (int index = descendants.size() - 1; index >= 0; index--) {
            if (forcibly) {
                descendants.get(index).destroyForcibly();
            } else {
                descendants.get(index).destroy();
            }
        }
    }

    private void copyOutput(
            Path source,
            Path destination,
            Runnable cancellationCheck) {
        if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) {
            throw outputFailure();
        }
        Set<java.nio.file.OpenOption> options = Set.of(
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
            deletePartialOutput(destination);
            throw new OperationException(
                "WORD_PDF_OUTPUT_LIMIT_EXCEEDED",
                "The converted PDF exceeds the configured output limit",
                exception
            );
        } catch (IOException exception) {
            deletePartialOutput(destination);
            throw new OperationException(
                "WORD_CONVERSION_OUTPUT_FAILED",
                "The converted PDF could not be finalized safely",
                exception
            );
        }
    }

    private void deletePartialOutput(Path output) {
        try {
            Files.deleteIfExists(output);
        } catch (IOException exception) {
            logger.error(
                "Could not remove partial Word-to-PDF output {}",
                output,
                exception
            );
        }
    }

    private void validateOutput(Path output) {
        try {
            if (Files.isSymbolicLink(output)
                    || !Files.isRegularFile(
                        output,
                        LinkOption.NOFOLLOW_LINKS
                    )) {
                throw outputFailure();
            }
            long size = Files.size(output);
            if (size < 1 || size > properties.getMaxOutputBytes()) {
                throw new OperationException(
                    "WORD_PDF_OUTPUT_LIMIT_EXCEEDED",
                    "The converted PDF exceeds the configured output limit"
                );
            }
            PdfInputValidator.requirePdfHeader(output);
            try (PDDocument document = Loader.loadPDF(output.toFile())) {
                if (document.isEncrypted()
                        || document.getNumberOfPages() < 1) {
                    throw outputFailure();
                }
            }
        } catch (OperationException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new OperationException(
                "INVALID_WORD_PDF_OUTPUT",
                "LibreOffice produced an unreadable PDF",
                exception
            );
        }
    }

    private void validateLogs(Path stdout, Path stderr) {
        try {
            if (!Files.isRegularFile(stdout, LinkOption.NOFOLLOW_LINKS)
                    || !Files.isRegularFile(
                        stderr,
                        LinkOption.NOFOLLOW_LINKS)
                    || Files.size(stdout) > properties.getMaxLogBytes()
                    || Files.size(stderr) > properties.getMaxLogBytes()) {
                throw new OperationException(
                    "WORD_CONVERTER_LOG_LIMIT_EXCEEDED",
                    "LibreOffice exceeded the configured log limit"
                );
            }
        } catch (IOException exception) {
            throw new OperationException(
                "WORD_CONVERTER_LOG_FAILED",
                "LibreOffice logs could not be inspected",
                exception
            );
        }
    }

    private void logFailure(Path stderr) {
        if (!Files.isRegularFile(stderr, LinkOption.NOFOLLOW_LINKS)) {
            logger.error(
                "LibreOffice Word conversion failed; stderr is not regular"
            );
            return;
        }
        Set<java.nio.file.OpenOption> options = Set.of(
            StandardOpenOption.READ,
            LinkOption.NOFOLLOW_LINKS
        );
        try (SeekableByteChannel channel =
                 Files.newByteChannel(stderr, options)) {
            long size = channel.size();
            channel.position(Math.max(size - 4096, 0));
            ByteBuffer buffer = ByteBuffer.allocate(
                (int) Math.min(size, 4096)
            );
            while (buffer.hasRemaining() && channel.read(buffer) >= 0) {
            }
            buffer.flip();
            String tail = StandardCharsets.UTF_8.decode(buffer).toString();
            logger.error("LibreOffice Word conversion failed: {}", tail);
        } catch (IOException exception) {
            logger.error(
                "LibreOffice Word conversion failed; stderr is unreadable",
                exception
            );
        }
    }

    private Map<String, String> environment(
            Path home,
            Path temporary,
            Path config,
            Path cache,
            Path data) {
        return Map.ofEntries(
            Map.entry("HOME", home.toAbsolutePath().toString()),
            Map.entry("TMPDIR", temporary.toAbsolutePath().toString()),
            Map.entry("XDG_CONFIG_HOME", config.toAbsolutePath().toString()),
            Map.entry("XDG_CACHE_HOME", cache.toAbsolutePath().toString()),
            Map.entry("XDG_DATA_HOME", data.toAbsolutePath().toString()),
            Map.entry("SAL_USE_VCLPLUGIN", "svp"),
            Map.entry("LANG", "C.UTF-8"),
            Map.entry("LC_ALL", "C.UTF-8"),
            Map.entry("USER", "pdftools"),
            Map.entry("LOGNAME", "pdftools"),
            Map.entry(
                "PATH",
                "/usr/local/bin:/usr/bin:/bin:/opt/homebrew/bin"
            )
        );
    }

    private Path resolveExecutable() {
        String configured = properties.getLibreOfficeBinary();
        if (configured == null || configured.isBlank()) {
            throw unavailable();
        }
        List<Path> candidates = new ArrayList<>();
        Path configuredPath = Path.of(configured);
        if (configuredPath.isAbsolute()
                || configured.contains(java.io.File.separator)) {
            candidates.add(configuredPath);
        } else {
            String path = System.getenv().getOrDefault("PATH", "");
            for (String directory : path.split(
                    java.util.regex.Pattern.quote(
                        java.io.File.pathSeparator
                    ))) {
                if (!directory.isBlank()) {
                    candidates.add(Path.of(directory, configured));
                }
            }
            candidates.add(Path.of("/opt/homebrew/bin", configured));
            candidates.add(Path.of("/usr/bin", configured));
            candidates.add(Path.of(
                "/Applications/LibreOffice.app/Contents/MacOS/soffice"
            ));
        }
        return candidates.stream()
            .filter(Files::isExecutable)
            .findFirst()
            .orElseThrow(this::unavailable);
    }

    private void writeProfile(Path profileUser) {
        try {
            Files.writeString(
                profileUser.resolve("registrymodifications.xcu"),
                PROFILE_CONFIGURATION
            );
        } catch (IOException exception) {
            throw new OperationException(
                "WORD_PROFILE_SETUP_FAILED",
                "The LibreOffice security profile could not be created",
                exception
            );
        }
    }

    private void copyInput(Path source, Path destination) {
        try {
            Files.copy(
                source,
                destination,
                StandardCopyOption.REPLACE_EXISTING
            );
        } catch (IOException exception) {
            throw new OperationException(
                "WORD_INPUT_PREPARATION_FAILED",
                "The Word document could not be prepared for conversion",
                exception
            );
        }
    }

    private void prepareWorkerOwnership(Path converterRoot) {
        if (!isLinux()
                || !properties.isIsolatedContainer()
                || System.getProperty("user.name").equals(
                    properties.getWorkerUser())) {
            return;
        }
        try {
            var owner = converterRoot.getFileSystem()
                .getUserPrincipalLookupService()
                .lookupPrincipalByName(properties.getWorkerUser());
            try (var paths = Files.walk(converterRoot)) {
                for (Path path : paths.toList()) {
                    Files.setOwner(path, owner);
                }
            }
        } catch (IOException exception) {
            throw new OperationException(
                "OFFICE_WORKER_IDENTITY_FAILED",
                "The Office worker identity could not be prepared",
                exception
            );
        }
    }

    private void createDirectories(Path... directories) {
        try {
            for (Path directory : directories) {
                Files.createDirectories(directory);
            }
        } catch (IOException exception) {
            throw new OperationException(
                "WORD_CONVERTER_WORKSPACE_FAILED",
                "The LibreOffice workspace could not be created",
                exception
            );
        }
    }

    private String extension(String filename) {
        return filename.toLowerCase(Locale.ROOT).endsWith(".docx")
            ? ".docx"
            : ".doc";
    }

    private OperationException unavailable() {
        return new OperationException(
            "LIBREOFFICE_UNAVAILABLE",
            "LibreOffice is not installed or executable"
        );
    }

    private OperationException outputFailure() {
        return new OperationException(
            "INVALID_WORD_PDF_OUTPUT",
            "LibreOffice did not produce a readable PDF"
        );
    }
}
