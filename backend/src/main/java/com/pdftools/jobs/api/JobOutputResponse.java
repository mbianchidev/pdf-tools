package com.pdftools.jobs.api;

import java.time.Instant;
import java.util.UUID;

public record JobOutputResponse(
    UUID id,
    int position,
    String filename,
    String mediaType,
    long sizeBytes,
    String sha256,
    Instant expiresAt,
    String downloadUrl
) {
}
