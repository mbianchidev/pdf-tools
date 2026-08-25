package com.pdftools.operations.office;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OfficeQueueProtocolTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void readsVersionOneWordAndPowerPointRequests() throws Exception {
        for (OfficeQueueProtocol.Request expected : new OfficeQueueProtocol.Request[]{
                new OfficeQueueProtocol.Request("word", ".docx", "{}"),
                new OfficeQueueProtocol.Request(
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

            assertEquals(expected, OfficeQueueProtocol.readRequest(request));
        }
    }

    @Test
    void keepsWordAndPowerPointWritersOnVersionOne() throws Exception {
        for (OfficeQueueProtocol.Request expected : new OfficeQueueProtocol.Request[]{
                new OfficeQueueProtocol.Request("word", ".doc", "{}"),
                new OfficeQueueProtocol.Request(
                    "powerpoint",
                    ".pptx",
                    "{}"
                )}) {
            Path request = temporaryDirectory.resolve(
                expected.type() + "-written.bin"
            );

            OfficeQueueProtocol.writeRequest(request, expected);

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
        OfficeQueueProtocol.Request expected =
            new OfficeQueueProtocol.Request(
                "excel",
                ".xlsx",
                "{\"orientation\":\"landscape\"}"
            );

        OfficeQueueProtocol.writeRequest(request, expected);

        try (DataInputStream input = new DataInputStream(
                new BufferedInputStream(Files.newInputStream(request)))) {
            assertEquals(2, input.readInt());
        }
        assertEquals(expected, OfficeQueueProtocol.readRequest(request));
    }
}
