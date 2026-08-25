package com.pdftools.storage;

public record StoredObject(
    String key,
    long sizeBytes,
    String sha256,
    String mediaType
) {
}
