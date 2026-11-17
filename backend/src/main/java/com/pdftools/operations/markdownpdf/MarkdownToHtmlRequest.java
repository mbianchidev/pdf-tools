package com.pdftools.operations.markdownpdf;

import com.pdftools.operations.OperationException;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

record MarkdownToHtmlRequest(
    Path source,
    MarkdownSourceKind kind,
    Path output,
    Path workspace,
    Limits limits
) {

    private static final int VERSION = 1;

    record Limits(
        long maxMarkdownBytes,
        int maxBundleEntries,
        int maxImages,
        long maxTotalImageBytes,
        long maxHtmlBytes
    ) {
        static Limits from(MarkdownProperties properties, long maxHtmlBytes) {
            return new Limits(
                properties.getMaxInputBytes(),
                properties.getMaxBundleEntries(),
                properties.getMaxImages(),
                properties.getMaxTotalImageBytes(),
                maxHtmlBytes
            );
        }

        boolean valid() {
            return maxMarkdownBytes > 0
                && maxBundleEntries > 0
                && maxImages >= 0
                && maxTotalImageBytes >= 0
                && maxHtmlBytes > 0;
        }
    }

    static void write(Path path, MarkdownToHtmlRequest request) {
        try (DataOutputStream data = new DataOutputStream(
                new BufferedOutputStream(Files.newOutputStream(path)))) {
            data.writeInt(VERSION);
            data.writeUTF(request.source().toAbsolutePath().toString());
            data.writeUTF(request.kind().name());
            data.writeUTF(request.output().toAbsolutePath().toString());
            data.writeUTF(request.workspace().toAbsolutePath().toString());
            data.writeLong(request.limits().maxMarkdownBytes());
            data.writeInt(request.limits().maxBundleEntries());
            data.writeInt(request.limits().maxImages());
            data.writeLong(request.limits().maxTotalImageBytes());
            data.writeLong(request.limits().maxHtmlBytes());
        } catch (IOException exception) {
            throw protocolFailure(exception);
        }
    }

    static MarkdownToHtmlRequest read(Path path) {
        try (DataInputStream data = new DataInputStream(
                new BufferedInputStream(Files.newInputStream(path)))) {
            if (data.readInt() != VERSION) {
                throw protocolFailure(null);
            }
            Path source = Path.of(data.readUTF());
            MarkdownSourceKind kind = MarkdownSourceKind.valueOf(
                data.readUTF()
            );
            Path output = Path.of(data.readUTF());
            Path workspace = Path.of(data.readUTF());
            Limits limits = new Limits(
                data.readLong(),
                data.readInt(),
                data.readInt(),
                data.readLong(),
                data.readLong()
            );
            if (data.read() != -1
                    || !source.isAbsolute()
                    || !output.isAbsolute()
                    || !workspace.isAbsolute()
                    || source.equals(output)
                    || !output.normalize().startsWith(workspace.normalize())
                    || !limits.valid()) {
                throw protocolFailure(null);
            }
            return new MarkdownToHtmlRequest(
                source,
                kind,
                output,
                workspace,
                limits
            );
        } catch (OperationException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw protocolFailure(exception);
        }
    }

    private static OperationException protocolFailure(Throwable cause) {
        return new OperationException(
            "MARKDOWN_WORKER_PROTOCOL_ERROR",
            "The Markdown worker received invalid state",
            cause
        );
    }
}
