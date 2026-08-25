package com.pdftools.operations.office;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class NativeProcessSandboxTest {

    @TempDir
    Path temporaryDirectory;

    private final OfficeConversionProperties properties =
        new OfficeConversionProperties();
    private final NativeProcessSandbox sandbox = new NativeProcessSandbox();

    @Test
    void deniesConverterNetworkAccess() throws Exception {
        assumeTrue(!isLinux() || sandbox.isSeccompFilterAvailable());
        Path workspace = temporaryDirectory.resolve("workspace");
        Files.createDirectories(workspace);
        try (ServerSocket server = new ServerSocket(
                0,
                1,
                InetAddress.getLoopbackAddress())) {
            server.setSoTimeout(3000);
            CompletableFuture<Void> responder = CompletableFuture.runAsync(
                () -> respond(server)
            );
            Process process = process(
                workspace,
                Path.of("/usr/bin/curl"),
                List.of(
                    "--silent",
                    "--show-error",
                    "--max-time",
                    "2",
                    "http://127.0.0.1:" + server.getLocalPort()
                )
            );

            assertNotEquals(0, process.waitFor());
            responder.cancel(true);
        }
    }

    @Test
    void deniesSiblingFileReadsOnLinux() throws Exception {
        assumeTrue(isLinux() && sandbox.isLandlockAvailable());
        Path workspace = temporaryDirectory.resolve("workspace");
        Files.createDirectories(workspace);
        Path sibling = temporaryDirectory.resolve("secret.txt");
        Files.writeString(sibling, "not available to the converter");

        Process process = process(
            workspace,
            Path.of("/usr/bin/cat"),
            List.of(sibling.toString())
        );

        assertNotEquals(0, process.waitFor());
    }

    @Test
    void permitsWorkspaceWrites() throws Exception {
        Path workspace = temporaryDirectory.resolve("workspace");
        Files.createDirectories(workspace);
        Path output = workspace.resolve("written.txt");

        Process process = process(
            workspace,
            Path.of("/bin/sh"),
            List.of("-c", "printf converted > \"$1\"", "sh", output.toString())
        );

        assertTrue(process.waitFor(10, TimeUnit.SECONDS));
        assertTrue(process.exitValue() == 0);
        assertTrue(Files.readString(output).equals("converted"));
    }

    @Test
    void pinsFilterToNativeAuditArchitecture() throws Exception {
        Path filter = temporaryDirectory.resolve("network.bpf");
        NetworkDenyFilter.write(filter);
        byte[] bytes = Files.readAllBytes(filter);

        assertEquals(14 * 8, bytes.length);
        ByteBuffer program = ByteBuffer.wrap(bytes)
            .order(ByteOrder.nativeOrder());
        int auditArchitecture = program.getInt(8 + 4);
        String architecture = System.getProperty("os.arch")
            .toLowerCase(java.util.Locale.ROOT);
        int expected = architecture.equals("amd64")
                || architecture.equals("x86_64")
            ? 0xC000003E
            : 0xC00000B7;
        assertEquals(expected, auditArchitecture);
    }

    private Process process(
            Path workspace,
            Path executable,
            List<String> arguments) throws IOException {
        properties.setWallTimeout(Duration.ofSeconds(10));
        properties.setIsolatedContainer(true);
        properties.setWorkerUser(System.getProperty("user.name"));
        properties.setMaxWorkerProcesses(4096);
        List<String> command = sandbox.command(
            workspace,
            workspace.resolve(".network-filter.bpf"),
            executable,
            arguments,
            properties
        );
        ProcessBuilder builder = new ProcessBuilder(command)
            .directory(workspace.toFile())
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD);
        builder.environment().clear();
        builder.environment().put("PATH", "/usr/local/bin:/usr/bin:/bin");
        builder.environment().put("HOME", workspace.toString());
        builder.environment().put("TMPDIR", workspace.toString());
        return builder.start();
    }

    private void respond(ServerSocket server) {
        try (Socket socket = server.accept();
             OutputStream output = socket.getOutputStream()) {
            output.write((
                "HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\nOK"
            ).getBytes(StandardCharsets.US_ASCII));
        } catch (IOException ignored) {
        }
    }

    private boolean isLinux() {
        return System.getProperty("os.name")
            .toLowerCase(java.util.Locale.ROOT)
            .contains("linux");
    }
}
