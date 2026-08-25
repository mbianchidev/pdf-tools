package com.pdftools.util;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;

public final class FilenameSanitizer {

    private static final int MAX_FILENAME_BYTES = 120;

    private FilenameSanitizer() {
    }

    public static String sanitize(String filename, String fallback) {
        String candidate = filename == null ? "" : filename.replace('\0', ' ').trim();
        candidate = candidate.replace('\\', '/');
        int lastSeparator = candidate.lastIndexOf('/');
        if (lastSeparator >= 0) {
            candidate = candidate.substring(lastSeparator + 1);
        }

        candidate = Normalizer.normalize(candidate, Normalizer.Form.NFKC)
            .replaceAll("[\\p{Cntrl}]", "")
            .replaceAll("[^\\p{L}\\p{N}._()\\[\\] -]", "_")
            .replaceAll("\\s+", " ")
            .trim();

        if (candidate.isBlank() || candidate.equals(".") || candidate.equals("..")) {
            candidate = fallback;
        }
        return truncateUtf8(candidate, MAX_FILENAME_BYTES);
    }

    public static String withSuffix(String filename, String suffix) {
        if (suffix == null || !suffix.matches("[-_A-Za-z0-9]+")) {
            throw new IllegalArgumentException("Filename suffix contains unsupported characters");
        }
        String sanitized = sanitize(filename, "output");
        NameParts parts = splitName(sanitized);
        int suffixBytes = suffix.getBytes(StandardCharsets.UTF_8).length;
        int extensionBytes = parts.extension().getBytes(StandardCharsets.UTF_8).length;
        int baseBudget = MAX_FILENAME_BYTES - suffixBytes - extensionBytes;
        if (baseBudget < 1) {
            throw new IllegalArgumentException("Filename suffix is too long");
        }
        return truncateRaw(parts.base(), baseBudget) + suffix + parts.extension();
    }

    private static String truncateUtf8(String filename, int maxBytes) {
        if (filename.getBytes(StandardCharsets.UTF_8).length <= maxBytes) {
            return filename;
        }

        NameParts parts = splitName(filename);
        int baseBudget = maxBytes - parts.extension().getBytes(StandardCharsets.UTF_8).length;
        return truncateRaw(parts.base(), baseBudget) + parts.extension();
    }

    private static String truncateRaw(String value, int maxBytes) {
        StringBuilder truncated = new StringBuilder();
        int usedBytes = 0;
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            String character = new String(Character.toChars(codePoint));
            int characterBytes = character.getBytes(StandardCharsets.UTF_8).length;
            if (usedBytes + characterBytes > maxBytes) {
                break;
            }
            truncated.append(character);
            usedBytes += characterBytes;
            offset += Character.charCount(codePoint);
        }
        return truncated.toString();
    }

    private static NameParts splitName(String filename) {
        int extensionStart = filename.lastIndexOf('.');
        String extension = extensionStart > 0 && filename.length() - extensionStart <= 16
            ? filename.substring(extensionStart)
            : "";
        String base = extension.isEmpty() ? filename : filename.substring(0, extensionStart);
        return new NameParts(base, extension);
    }

    private record NameParts(String base, String extension) {
    }
}
