package com.pdftools.operations;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

public class CheckpointInputStream extends FilterInputStream {

    private static final long CHECKPOINT_BYTES = 1024L * 1024L;

    private final Runnable checkpoint;
    private long count;
    private long nextCheckpoint = CHECKPOINT_BYTES;

    public CheckpointInputStream(InputStream input, Runnable checkpoint) {
        super(input);
        this.checkpoint = checkpoint;
    }

    @Override
    public int read() throws IOException {
        int value = in.read();
        if (value >= 0) {
            afterRead(1);
        }
        return value;
    }

    @Override
    public int read(byte[] bytes, int offset, int length) throws IOException {
        int read = in.read(bytes, offset, length);
        if (read > 0) {
            afterRead(read);
        }
        return read;
    }

    private void afterRead(int bytes) {
        count += bytes;
        if (count >= nextCheckpoint) {
            checkpoint.run();
            nextCheckpoint = count + CHECKPOINT_BYTES;
        }
    }
}
