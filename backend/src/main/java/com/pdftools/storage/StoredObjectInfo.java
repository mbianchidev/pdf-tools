package com.pdftools.storage;

import java.time.Instant;

public record StoredObjectInfo(
    String key,
    Instant lastModified
) {
}
