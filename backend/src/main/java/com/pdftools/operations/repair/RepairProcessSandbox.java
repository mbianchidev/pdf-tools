package com.pdftools.operations.repair;

import com.pdftools.operations.OperationException;
import com.pdftools.operations.shared.worker.NetworkDenyFilter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class RepairProcessSandbox {

    private static final String MACOS_PROFILE =
        "(version 1)(allow default)(deny network*)";

    private final RepairPdfProperties properties;

    RepairProcessSandbox(RepairPdfProperties properties) {
        this.properties = properties;
    }

    List<String> command(
            Path workspace,
            Path networkFilter,
            List<String> arguments,
            long maxFileBytes) {
        validate(maxFileBytes);
        String os = System.getProperty("os.name")
            .toLowerCase(Locale.ROOT);
        if (os.contains("linux")) {
            return linux(networkFilter, arguments, maxFileBytes);
        }
        if (os.contains("mac")) {
            return macos(arguments, maxFileBytes);
        }
        throw new OperationException(
            "REPAIR_SANDBOX_UNAVAILABLE",
            "PDF repair requires Linux or macOS process isolation"
        );
    }

    Map<String, String> environment(Path workspace) {
        return Map.of(
            "HOME", workspace.toAbsolutePath().toString(),
            "TMPDIR", workspace.toAbsolutePath().toString(),
            "PATH", "/opt/homebrew/bin:/usr/local/bin:/usr/bin:/bin",
            "LC_ALL", "C"
        );
    }

    private List<String> linux(
            Path networkFilter,
            List<String> arguments,
            long maxFileBytes) {
        requireExecutable("/usr/bin/setsid");
        requireExecutable("/usr/bin/prlimit");
        requireExecutable("/usr/bin/setpriv");
        NetworkDenyFilter.write(networkFilter);
        List<String> command = new ArrayList<>(List.of(
            "/usr/bin/setsid",
            "/usr/bin/prlimit",
            "--as=" + properties.getMaxAddressSpaceBytes(),
            "--cpu=" + properties.getCpuTimeSeconds(),
            "--fsize=" + maxFileBytes,
            "--nofile=" + properties.getMaxOpenFiles(),
            "--",
            "/usr/bin/setpriv",
            "--no-new-privs",
            "--inh-caps=-all",
            "--ambient-caps=-all",
            "--seccomp-filter",
            networkFilter.toAbsolutePath().toString(),
            "--",
            qpdf()
        ));
        command.addAll(arguments);
        return List.copyOf(command);
    }

    private List<String> macos(
            List<String> arguments,
            long maxFileBytes) {
        requireExecutable("/bin/sh");
        requireExecutable("/usr/bin/sandbox-exec");
        long fileBlocks = Math.max((maxFileBytes + 511) / 512, 1);
        List<String> command = new ArrayList<>(List.of(
            "/bin/sh",
            "-c",
            "ulimit -t \"$1\" && ulimit -f \"$2\" "
                + "&& ulimit -n \"$3\" && shift 3 && exec \"$@\"",
            "repair-limits",
            Integer.toString(properties.getCpuTimeSeconds()),
            Long.toString(fileBlocks),
            Integer.toString(properties.getMaxOpenFiles()),
            "/usr/bin/sandbox-exec",
            "-p",
            MACOS_PROFILE,
            qpdf()
        ));
        command.addAll(arguments);
        return List.copyOf(command);
    }

    private String qpdf() {
        String value = properties.getQpdfBinary();
        if (value == null
                || value.isBlank()
                || value.length() > 1024) {
            throw unavailable();
        }
        String trimmed = value.trim();
        if (!trimmed.contains("/")) {
            if (!trimmed.matches("[A-Za-z0-9._+-]{1,64}")) {
                throw unavailable();
            }
            return trimmed;
        }
        Path path = Path.of(trimmed);
        if (!path.isAbsolute()
                || !Files.isRegularFile(path)
                || !Files.isExecutable(path)) {
            throw unavailable();
        }
        return path.toString();
    }

    private void validate(long maxFileBytes) {
        if (maxFileBytes < 1
                || properties.getMaxAddressSpaceBytes() < 1
                || properties.getCpuTimeSeconds() < 1
                || properties.getMaxOpenFiles() < 16) {
            throw new IllegalStateException(
                "PDF repair process limits are invalid"
            );
        }
    }

    private void requireExecutable(String value) {
        Path path = Path.of(value);
        if (!Files.isRegularFile(path) || !Files.isExecutable(path)) {
            throw new OperationException(
                "REPAIR_SANDBOX_UNAVAILABLE",
                "PDF repair process isolation is unavailable"
            );
        }
    }

    private OperationException unavailable() {
        return new OperationException(
            "QPDF_UNAVAILABLE",
            "The configured qpdf executable is unavailable"
        );
    }
}
