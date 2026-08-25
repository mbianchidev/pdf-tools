package com.pdftools.operations.shared.queue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConversionQueueProtocolTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void readsVersionOneWordAndPowerPointRequests() throws Exception {
        for (ConversionQueueProtocol.Request expected : new ConversionQueueProtocol.Request[]{
                new ConversionQueueProtocol.Request("word", ".docx", "{}"),
                new ConversionQueueProtocol.Request(
                    "powerpoint",
                    ".ppt",
                    "{}"
                )}) {
            Path request = temporaryDirectory.resolve(
                expected.type() + ".bin"
            );
            try (DataOutputStream output = new DataOutputStream(
                    new BufferedOutputStream(
                        Files.newOutputStream(request)))) {
                output.writeInt(1);
                output.writeUTF(expected.type());
                output.writeUTF(expected.extension());
            }

            assertEquals(
                expected,
                ConversionQueueProtocol.readRequest(request)
            );
        }
    }

    @Test
    void keepsWordAndPowerPointWritersOnVersionOne() throws Exception {
        for (ConversionQueueProtocol.Request expected : new ConversionQueueProtocol.Request[]{
                new ConversionQueueProtocol.Request("word", ".doc", "{}"),
                new ConversionQueueProtocol.Request(
                    "powerpoint",
                    ".pptx",
                    "{}"
                )}) {
            Path request = temporaryDirectory.resolve(
                expected.type() + "-written.bin"
            );

            ConversionQueueProtocol.writeRequest(request, expected);

            try (DataInputStream input = new DataInputStream(
                    new BufferedInputStream(Files.newInputStream(request)))) {
                assertEquals(1, input.readInt());
                assertEquals(expected.type(), input.readUTF());
                assertEquals(expected.extension(), input.readUTF());
                assertEquals(-1, input.read());
            }
        }
    }

    @Test
    void writesAndReadsVersionTwoExcelOptions() throws Exception {
        Path request = temporaryDirectory.resolve("excel.bin");
        ConversionQueueProtocol.Request expected =
            new ConversionQueueProtocol.Request(
                "excel",
                ".xlsx",
                "{\"orientation\":\"landscape\"}"
            );

        ConversionQueueProtocol.writeRequest(request, expected);

        try (DataInputStream input = new DataInputStream(
                new BufferedInputStream(Files.newInputStream(request)))) {
            assertEquals(2, input.readInt());
        }
        assertEquals(expected, ConversionQueueProtocol.readRequest(request));
    }

    @Test
    void writesAndReadsVersionTwoPdfAOptions() throws Exception {
        Path request = temporaryDirectory.resolve("pdfa.bin");
        ConversionQueueProtocol.Request expected =
            new ConversionQueueProtocol.Request(
                "pdfa",
                ".pdf",
                "{\"profile\":\"pdfa-2b\"}"
            );

        ConversionQueueProtocol.writeRequest(request, expected);

        try (DataInputStream input = new DataInputStream(
                new BufferedInputStream(Files.newInputStream(request)))) {
            assertEquals(2, input.readInt());
        }
        assertEquals(expected, ConversionQueueProtocol.readRequest(request));
    }
}
