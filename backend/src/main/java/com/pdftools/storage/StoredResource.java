package com.pdftools.storage;

import java.io.IOException;
import java.io.InputStream;

public record StoredResource(
    InputStream inputStream,
    long sizeBytes,
    String mediaType
) implements AutoCloseable {

    @Override
    public void close() throws IOException {
        inputStream.close();
    }
}
