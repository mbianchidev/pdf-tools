package com.pdftools.operations.markdownpdf;

import com.pdftools.operations.OperationException;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns Markdown image URLs into self-contained {@code data:} URIs. Only
 * bundle-relative files and existing base64 data URIs whose bytes are PNG,
 * JPEG, GIF, or WebP are embedded; everything else resolves to {@code null}
 * so the caller renders alt text instead of letting the browser fetch it.
 */
final class MarkdownImageEmbedder {

    interface ImageSource {
        /** Up to {@code length} leading bytes, or null when absent. */
        byte[] header(String path, int length);

        byte[] read(String path, long maxBytes);
    }

    private static final int HEADER_BYTES = 12;
    private static final int HEADER_BASE64_CHARACTERS = 16;

    private static final Pattern SCHEME =
        Pattern.compile("^[A-Za-z][A-Za-z0-9+.\\-]*:");
    private static final Pattern DATA_URI = Pattern.compile(
        "^data:image/(?:png|jpeg|jpg|gif|webp);base64,(.*)$",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    private final ImageSource source;
    private final String baseDirectory;
    private final int maxImages;
    private final long maxTotalBytes;
    private final long maxEmbeddedCharacters;
    private final Map<String, Optional<String>> embedded = new HashMap<>();
    private int imageCount;
    private long imageBytes;
    private long embeddedCharacters;

    MarkdownImageEmbedder(
            ImageSource source,
            String baseDirectory,
            int maxImages,
            long maxTotalBytes,
            long maxEmbeddedCharacters) {
        this.source = source;
        this.baseDirectory = baseDirectory;
        this.maxImages = maxImages;
        this.maxTotalBytes = maxTotalBytes;
        this.maxEmbeddedCharacters = maxEmbeddedCharacters;
    }

    String resolve(String url) {
        String trimmed = url == null ? "" : url.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        Matcher data = DATA_URI.matcher(trimmed);
        if (data.matches()) {
            String payload = data.group(1);
            return track(embedded.computeIfAbsent(
                trimmed,
                ignored -> Optional.ofNullable(fromDataUri(payload))
            ));
        }
        if (source == null
                || SCHEME.matcher(trimmed).find()
                || trimmed.startsWith("//")) {
            return null;
        }
        String path = bundlePath(trimmed);
        if (path == null) {
            return null;
        }
        return track(embedded.computeIfAbsent(
            path,
            key -> Optional.ofNullable(fromBundle(key))
        ));
    }

    private String track(Optional<String> dataUri) {
        if (dataUri.isEmpty()) {
            return null;
        }
        embeddedCharacters += dataUri.get().length();
        if (embeddedCharacters > maxEmbeddedCharacters) {
            throw MarkdownToHtmlRenderer.renderedTooLarge();
        }
        return dataUri.get();
    }

    /*
     * Missing, malformed, and unsupported images fall back to alt text before
     * any count or byte budget applies; only accepted images use budgets.
     */
    private String fromBundle(String path) {
        byte[] header = source.header(path, HEADER_BYTES);
        if (header == null || mediaType(header) == null) {
            return null;
        }
        requireImageSlot();
        return encode(source.read(path, maxTotalBytes - imageBytes));
    }

    private String fromDataUri(String payload) {
        String compact = payload.replaceAll("\\s", "");
        if (mediaType(decode(compact.substring(
                0,
                Math.min(compact.length(), HEADER_BASE64_CHARACTERS)
            ))) == null) {
            return null;
        }
        requireImageSlot();
        if ((long) compact.length() / 4L * 3L
                > maxTotalBytes - imageBytes + 3L) {
            throw imagesTooLarge();
        }
        byte[] bytes = decode(compact);
        return bytes.length == 0 ? null : encode(bytes);
    }

    private static byte[] decode(String base64) {
        try {
            return Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException exception) {
            return new byte[0];
        }
    }

    private String encode(byte[] bytes) {
        String mediaType = mediaType(bytes);
        if (mediaType == null) {
            return null;
        }
        if (bytes.length > maxTotalBytes - imageBytes) {
            throw imagesTooLarge();
        }
        imageCount++;
        imageBytes += bytes.length;
        return "data:" + mediaType + ";base64,"
            + Base64.getEncoder().encodeToString(bytes);
    }

    private void requireImageSlot() {
        if (imageCount >= maxImages) {
            throw new OperationException(
                "MARKDOWN_IMAGE_LIMIT_EXCEEDED",
                "The Markdown document references too many images"
            );
        }
    }

    private String bundlePath(String url) {
        String withoutSuffix = url;
        int suffix = indexOfAny(withoutSuffix, '?', '#');
        if (suffix >= 0) {
            withoutSuffix = withoutSuffix.substring(0, suffix);
        }
        String decoded;
        try {
            decoded = URLDecoder.decode(
                withoutSuffix.replace("+", "%2B"),
                StandardCharsets.UTF_8
            );
        } catch (IllegalArgumentException exception) {
            return null;
        }
        if (decoded.isEmpty()
                || decoded.startsWith("/")
                || decoded.indexOf('\\') >= 0
                || decoded.indexOf('\u0000') >= 0) {
            return null;
        }
        Deque<String> segments = new ArrayDeque<>();
        for (String segment : (baseDirectory + decoded).split("/")) {
            if (segment.isEmpty() || segment.equals(".")) {
                continue;
            }
            if (segment.equals("..")) {
                if (segments.isEmpty()) {
                    return null;
                }
                segments.removeLast();
            } else {
                segments.addLast(segment);
            }
        }
        return segments.isEmpty() ? null : String.join("/", segments);
    }

    private static int indexOfAny(String value, char first, char second) {
        int a = value.indexOf(first);
        int b = value.indexOf(second);
        if (a < 0) {
            return b;
        }
        return b < 0 ? a : Math.min(a, b);
    }

    static String mediaType(byte[] bytes) {
        if (startsWith(bytes, 0, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
            return "image/png";
        }
        if (startsWith(bytes, 0, 0xFF, 0xD8, 0xFF)) {
            return "image/jpeg";
        }
        if (startsWith(bytes, 0, 'G', 'I', 'F', '8', '7', 'a')
                || startsWith(bytes, 0, 'G', 'I', 'F', '8', '9', 'a')) {
            return "image/gif";
        }
        if (startsWith(bytes, 0, 'R', 'I', 'F', 'F')
                && startsWith(bytes, 8, 'W', 'E', 'B', 'P')) {
            return "image/webp";
        }
        return null;
    }

    private static boolean startsWith(byte[] bytes, int offset, int... prefix) {
        if (bytes.length < offset + prefix.length) {
            return false;
        }
        for (int index = 0; index < prefix.length; index++) {
            if ((bytes[offset + index] & 0xFF) != prefix[index]) {
                return false;
            }
        }
        return true;
    }

    private static OperationException imagesTooLarge() {
        return new OperationException(
            "MARKDOWN_IMAGE_SIZE_LIMIT_EXCEEDED",
            "Embedded images exceed the configured size limit"
        );
    }
}
