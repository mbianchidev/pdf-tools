package com.pdftools.util;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FilenameSanitizerTest {

    @Test
    void limitsUtf8BytesAndPreservesTheExtension() {
        String sanitized = FilenameSanitizer.sanitize("文".repeat(160) + ".pdf", "input.pdf");

        assertTrue(sanitized.getBytes(StandardCharsets.UTF_8).length <= 120);
        assertTrue(sanitized.endsWith(".pdf"));
    }

    @Test
    void appendsSuffixWithinTheByteLimit() {
        String suffixed = FilenameSanitizer.withSuffix(
            "文".repeat(160) + ".pdf",
            "-2"
        );

        assertTrue(suffixed.getBytes(StandardCharsets.UTF_8).length <= 120);
        assertTrue(suffixed.endsWith("-2.pdf"));
    }
}
