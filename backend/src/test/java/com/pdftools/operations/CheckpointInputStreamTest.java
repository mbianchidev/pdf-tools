package com.pdftools.operations;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;

import static org.junit.jupiter.api.Assertions.assertThrows;

class CheckpointInputStreamTest {

    @Test
    void interruptsLongStorageReadsAtBoundedIntervals() {
        CheckpointInputStream input = new CheckpointInputStream(
            new ByteArrayInputStream(new byte[2 * 1024 * 1024]),
            () -> {
                throw new OperationCancelledException();
            }
        );

        assertThrows(OperationCancelledException.class, input::readAllBytes);
    }

    @Test
    void interruptsLongSkippedRegionsAtBoundedIntervals() {
        CheckpointInputStream input = new CheckpointInputStream(
            new ByteArrayInputStream(new byte[2 * 1024 * 1024]),
            () -> {
                throw new OperationCancelledException();
            }
        );

        assertThrows(
            OperationCancelledException.class,
            () -> input.skipNBytes(2 * 1024 * 1024)
        );
    }
}
