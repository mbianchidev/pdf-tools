package com.pdftools.operations.office;

import com.pdftools.operations.OperationException;
import com.pdftools.operations.shared.worker.NetworkDenyFilter;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class NativeProcessSandbox {

    private static final String HANDLED_FILESYSTEM_ACCESS = String.join(
        ",",
        "execute",
        "write-file",
        "read-file",
        "read-dir",
        "remove-dir",
        "remove-file",
        "make-dir",
        "make-reg",
        "make-sock",
        "make-fifo",
        "make-sym",
        "refer",
        "truncate"
    );
    private static final String WORKSPACE_ACCESS = String.join(
        ",",
        "execute",
        "write-file",
        "read-file",
        "read-dir",
        "remove-dir",
        "remove-file",
        "make-dir",
        "make-reg",
        "make-sock",
        "make-sym",
        "refer",
        "truncate"
    );
    private static final String READ_ACCESS = "execute,read-file,read-dir";
    private static final String DEVICE_ACCESS =
        "read-file,write-file";
    private static final String MACOS_PROFILE =
        "(version 1)(allow default)(deny network*)"
            + "(allow network-outbound (to unix-socket))"
            + "(allow network-bind (to unix-socket))";
    private static volatile Boolean landlockAvailable;
    private static volatile Boolean seccompFilterAvailable;

    public List<String> command(
            Path workspace,
            Path networkFilter,
            Path executable,
            List<String> arguments,
            OfficeConversionProperties properties) {
        return command(
            workspace,
            networkFilter,
            executable,
            arguments,
            properties,
            List.of()
        );
    }

    public List<String> command(
            Path workspace,
            Path networkFilter,
            Path executable,
            List<String> arguments,
            OfficeConversionProperties properties,
            List<Path> additionalReadRoots) {
        return command(
            workspace,
            networkFilter,
            executable,
            arguments,
            properties,
            additionalReadRoots,
            properties.getMaxAddressSpaceBytes()
        );
    }

    public List<String> command(
            Path workspace,
            Path networkFilter,
            Path executable,
            List<String> arguments,
            OfficeConversionProperties properties,
            List<Path> additionalReadRoots,
            long maxAddressSpaceBytes) {
        validate(properties, maxAddressSpaceBytes);
        String os = System.getProperty("os.name")
            .toLowerCase(Locale.ROOT);
        if (os.contains("linux")) {
            return linux(
                workspace,
                networkFilter,
                executable,
                arguments,
                properties,
                additionalReadRoots,
                maxAddressSpaceBytes
            );
        }
        if (os.contains("mac")) {
            return macos(executable, arguments, properties);
        }
        throw new OperationException(
            "OFFICE_SANDBOX_UNAVAILABLE",
            "Office conversion requires a supported process sandbox"
        );
    }

    private List<String> linux(
            Path workspace,
            Path networkFilter,
            Path executable,
            List<String> arguments,
            OfficeConversionProperties properties,
            List<Path> additionalReadRoots,
            long maxAddressSpaceBytes) {
        if (!properties.isIsolatedContainer()) {
            throw new OperationException(
                "OFFICE_DIRECT_LINUX_UNSUPPORTED",
                "Linux Office conversion requires the isolated sidecar"
            );
        }
        requireExecutable("/usr/bin/prlimit");
        requireExecutable("/usr/bin/setpriv");
        List<String> command = new ArrayList<>();
        requireExecutable("/usr/bin/setsid");
        command.add("/usr/bin/setsid");
        command.add("/usr/bin/prlimit");
        command.add("--as=" + maxAddressSpaceBytes);
        command.add("--cpu=" + properties.getCpuTimeSeconds());
        command.add("--fsize=" + processFileLimit(properties));
        command.add("--nofile=" + properties.getMaxOpenFiles());
        if (!properties.getWorkerUser().equals(
                System.getProperty("user.name"))) {
            command.add("--nproc=" + properties.getMaxWorkerProcesses());
        }
        command.add("--");
        command.add("/usr/bin/setpriv");
        String workerUser = properties.getWorkerUser();
        if (workerUser == null
                || !workerUser.matches("[a-z_][a-z0-9_-]{0,31}")) {
            throw new IllegalStateException(
                "Office worker user is invalid"
            );
        }
        if (!workerUser.equals(System.getProperty("user.name"))) {
            command.add("--reuid=" + workerUser);
            command.add("--regid=" + workerUser);
            command.add("--clear-groups");
        }
        command.add("--no-new-privs");
        command.add("--inh-caps=-all");
        command.add("--ambient-caps=-all");
        boolean landlock = isLandlockAvailable();
        if (!landlock && !workerUser.equals(
                System.getProperty("user.name"))) {
            throw new OperationException(
                "OFFICE_SANDBOX_UNAVAILABLE",
                "The isolated Office worker requires Landlock support"
            );
        }
        if (landlock) {
            command.add("--landlock-access");
            command.add("fs:" + HANDLED_FILESYSTEM_ACCESS);
            addReadRules(command, additionalReadRoots);
            command.add("--landlock-rule");
            command.add(
                "path-beneath:" + WORKSPACE_ACCESS + ":"
                    + workspace.toAbsolutePath()
            );
        }
        boolean seccomp = isSeccompFilterAvailable();
        if (!seccomp && !workerUser.equals(
                System.getProperty("user.name"))) {
            throw new OperationException(
                "OFFICE_SANDBOX_UNAVAILABLE",
                "The isolated Office worker requires seccomp support"
            );
        }
        if (seccomp) {
            NetworkDenyFilter.write(networkFilter);
            command.add("--seccomp-filter");
            command.add(networkFilter.toAbsolutePath().toString());
        }
        command.add("--");
        command.add(executable.toString());
        command.addAll(arguments);
        return List.copyOf(command);
    }

    private void addReadRules(
            List<String> command,
            List<Path> additionalReadRoots) {
        for (String candidate : List.of(
                "/usr",
                "/lib",
                "/lib64",
                "/etc",
                "/var/cache/fontconfig",
                "/var/lib/libreoffice")) {
            if (Files.exists(Path.of(candidate))) {
                command.add("--landlock-rule");
                command.add(
                    "path-beneath:" + READ_ACCESS + ":" + candidate
                );
            }
        }
        for (String directory : List.of("/", "/var/tmp", "/dev")) {
            addRule(command, "read-dir", directory);
        }
        addRule(command, "read-dir,make-sock,remove-file", "/tmp");
        addRule(command, "read-file,read-dir", "/proc");
        addRule(command, "read-file,read-dir", "/sys");
        for (String device : List.of(
                "/dev/null",
                "/dev/zero",
                "/dev/random",
                "/dev/urandom")) {
            addRule(command, DEVICE_ACCESS, device);
        }
        for (Path path : additionalReadRoots.stream()
                .map(Path::toAbsolutePath)
                .map(Path::normalize)
                .distinct()
                .toList()) {
            if (Files.exists(path)) {
                addRule(
                    command,
                    Files.isDirectory(path) ? READ_ACCESS : "read-file",
                    path.toString()
                );
            }
        }
    }

    public void prepareWorkspace(
            Path workspace,
            OfficeConversionProperties properties) {
        if (!System.getProperty("os.name")
                    .toLowerCase(Locale.ROOT)
                    .contains("linux")
                || !properties.isIsolatedContainer()
                || System.getProperty("user.name").equals(
                    properties.getWorkerUser())) {
            return;
        }
        try {
            var owner = workspace.getFileSystem()
                .getUserPrincipalLookupService()
                .lookupPrincipalByName(properties.getWorkerUser());
            try (var paths = Files.walk(workspace)) {
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

    private void addRule(
            List<String> command,
            String access,
            String path) {
        if (Files.exists(Path.of(path))) {
            command.add("--landlock-rule");
            command.add("path-beneath:" + access + ":" + path);
        }
    }

    private List<String> macos(
            Path executable,
            List<String> arguments,
            OfficeConversionProperties properties) {
        requireExecutable("/usr/bin/sandbox-exec");
        long fileBlocks = Math.max(
            (processFileLimit(properties) + 511) / 512,
            1
        );
        List<String> command = new ArrayList<>();
        command.add("/bin/sh");
        command.add("-c");
        command.add(
            "ulimit -t \"$1\" && ulimit -f \"$2\" "
                + "&& ulimit -n \"$3\" && shift 3 && exec \"$@\""
        );
        command.add("office-limits");
        command.add(Long.toString(properties.getCpuTimeSeconds()));
        command.add(Long.toString(fileBlocks));
        command.add(Integer.toString(properties.getMaxOpenFiles()));
        command.add("/usr/bin/sandbox-exec");
        command.add("-p");
        command.add(MACOS_PROFILE);
        command.add(executable.toString());
        command.addAll(arguments);
        return List.copyOf(command);
    }

    private void validate(
            OfficeConversionProperties properties,
            long maxAddressSpaceBytes) {
        Duration timeout = properties.getWallTimeout();
        if (maxAddressSpaceBytes < 64L * 1024L * 1024L
                || properties.getCpuTimeSeconds() < 1
                || properties.getMaxOutputBytes() < 1
                || properties.getMaxOpenFiles() < 16
                || properties.getMaxWorkerProcesses() < 8
                || timeout == null
                || timeout.isZero()
                || timeout.isNegative()
                || timeout.compareTo(Duration.ofHours(1)) > 0) {
            throw new IllegalStateException(
                "Office converter limits are invalid"
            );
        }
    }

    public boolean isLandlockAvailable() {
        Boolean available = landlockAvailable;
        if (available != null) {
            return available;
        }
        synchronized (NativeProcessSandbox.class) {
            if (landlockAvailable == null) {
                landlockAvailable = detectLandlock();
            }
            return landlockAvailable;
        }
    }

    public boolean isSeccompFilterAvailable() {
        Boolean available = seccompFilterAvailable;
        if (available != null) {
            return available;
        }
        synchronized (NativeProcessSandbox.class) {
            if (seccompFilterAvailable == null) {
                seccompFilterAvailable = detectSetprivOption(
                    "--seccomp-filter"
                );
            }
            return seccompFilterAvailable;
        }
    }

    private boolean detectLandlock() {
        return detectSetprivOption("--landlock-access");
    }

    private boolean detectSetprivOption(String option) {
        try {
            Process process = new ProcessBuilder(
                "/usr/bin/setpriv",
                "--help"
            )
                .redirectErrorStream(true)
                .start();
            String help = new String(
                process.getInputStream().readNBytes(32 * 1024),
                java.nio.charset.StandardCharsets.UTF_8
            );
            return process.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)
                && process.exitValue() == 0
                && help.contains(option);
        } catch (IOException exception) {
            return false;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private long processFileLimit(
            OfficeConversionProperties properties) {
        return Math.max(
            properties.getMaxOutputBytes(),
            properties.getMaxLogBytes()
        );
    }

    private void requireExecutable(String path) {
        if (!Files.isExecutable(Path.of(path))) {
            throw new OperationException(
                "OFFICE_SANDBOX_UNAVAILABLE",
                "Office conversion requires the configured process sandbox"
            );
        }
    }
}
