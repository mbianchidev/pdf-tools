package com.pdftools.operations.shared.worker;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public final class NativeSandboxCapabilities {

    private static final Map<String, Boolean> SETPRIV_OPTIONS =
        new ConcurrentHashMap<>();

    private NativeSandboxCapabilities() {
    }

    public static boolean supportsSetprivOption(String option) {
        return SETPRIV_OPTIONS.computeIfAbsent(
            option,
            NativeSandboxCapabilities::detectSetprivOption
        );
    }

    private static boolean detectSetprivOption(String option) {
        try {
            Process process = new ProcessBuilder(
                "/usr/bin/setpriv",
                "--help"
            )
                .redirectErrorStream(true)
                .start();
            String help = new String(
                process.getInputStream().readNBytes(32 * 1024),
                StandardCharsets.UTF_8
            );
            return process.waitFor(2, TimeUnit.SECONDS)
                && process.exitValue() == 0
                && help.contains(option);
        } catch (IOException exception) {
            return false;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
