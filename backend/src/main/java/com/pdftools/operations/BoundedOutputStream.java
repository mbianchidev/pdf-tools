package com.pdftools.operations;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class BoundedOutputStream extends FilterOutputStream {

    private static final long CHECKPOINT_BYTES = 1024L * 1024L;

    private final long maxBytes;
    private final Runnable checkpoint;
    private long count;
    private long nextCheckpoint = CHECKPOINT_BYTES;

    public BoundedOutputStream(
            OutputStream output,
            long maxBytes,
            Runnable checkpoint) {
        super(output);
        if (maxBytes < 0) {
            throw new IllegalArgumentException("maxBytes must not be negative");
        }
        this.maxBytes = maxBytes;
        this.checkpoint = checkpoint;
    }

    @Override
    public void write(int value) throws IOException {
        requireCapacity(1);
        out.write(value);
        afterWrite(1);
    }

    @Override
    public void write(byte[] bytes, int offset, int length) throws IOException {
        requireCapacity(length);
        out.write(bytes, offset, length);
        afterWrite(length);
    }

    public long getCount() {
        return count;
    }

    private void requireCapacity(int length) throws OutputLimitExceededException {
        if (length < 0 || count > maxBytes - length) {
            throw new OutputLimitExceededException(maxBytes);
        }
    }

    private void afterWrite(int length) {
        count += length;
        if (count >= nextCheckpoint) {
            checkpoint.run();
            nextCheckpoint = count + CHECKPOINT_BYTES;
        }
    }
}
