package com.pdftools.operations.markdownpdf;

import com.pdftools.operations.OperationException;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Read-only view of a Markdown ZIP bundle. Only the selected Markdown entry
 * and images it references are ever decompressed, each through a bounded
 * stream, so unreferenced or oversized entries cannot exhaust memory.
 */
final class MarkdownBundle implements Closeable {

    private static final String PREFERRED_DOCUMENT = "document.md";

    private final ZipFile zip;
    private final Map<String, ZipEntry> files;
    private final String markdownName;

    private MarkdownBundle(
            ZipFile zip,
            Map<String, ZipEntry> files,
            String markdownName) {
        this.zip = zip;
        this.files = files;
        this.markdownName = markdownName;
    }

    static MarkdownBundle open(Path source, int maxEntries) {
        ZipFile zip;
        try {
            zip = new ZipFile(source.toFile());
        } catch (IOException exception) {
            throw invalidBundle(
                "The ZIP bundle could not be read",
                exception
            );
        }
        try {
            if (zip.size() > maxEntries) {
                throw new OperationException(
                    "MARKDOWN_BUNDLE_ENTRY_LIMIT_EXCEEDED",
                    "The ZIP bundle contains too many entries"
                );
            }
            Map<String, ZipEntry> files = new HashMap<>();
            Enumeration<? extends ZipEntry> entries = zip.entries();
            int count = 0;
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (++count > maxEntries) {
                    throw new OperationException(
                        "MARKDOWN_BUNDLE_ENTRY_LIMIT_EXCEEDED",
                        "The ZIP bundle contains too many entries"
                    );
                }
                String name = entry.getName();
                requireSafeName(name);
                if (entry.isDirectory() || ignored(name)) {
                    continue;
                }
                if (files.putIfAbsent(name, entry) != null) {
                    throw invalidBundle(
                        "The ZIP bundle contains duplicate entries",
                        null
                    );
                }
            }
            return new MarkdownBundle(zip, files, selectMarkdown(files));
        } catch (RuntimeException exception) {
            closeQuietly(zip, exception);
            throw exception;
        }
    }

    String markdownName() {
        return markdownName;
    }

    String markdownDirectory() {
        int separator = markdownName.lastIndexOf('/');
        return separator < 0 ? "" : markdownName.substring(0, separator + 1);
    }

    byte[] readMarkdown(long maxBytes) {
        return read(
            files.get(markdownName),
            maxBytes,
            () -> new OperationException(
                "MARKDOWN_INPUT_SIZE_LIMIT_EXCEEDED",
                "The bundled Markdown document exceeds the configured input limit"
            )
        );
    }

    /**
     * Returns the entry bytes, or {@code null} when the bundle has no such
     * file.
     */
    byte[] readFile(String name, long maxBytes) {
        ZipEntry entry = files.get(name);
        if (entry == null) {
            return null;
        }
        return read(
            entry,
            maxBytes,
            () -> new OperationException(
                "MARKDOWN_IMAGE_SIZE_LIMIT_EXCEEDED",
                "Bundled images exceed the configured size limit"
            )
        );
    }

    @Override
    public void close() throws IOException {
        zip.close();
    }

    private byte[] read(
            ZipEntry entry,
            long maxBytes,
            Supplier<OperationException> tooLarge) {
        try (InputStream input = zip.getInputStream(entry)) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            long total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) {
                    throw tooLarge.get();
                }
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        } catch (IOException exception) {
            throw invalidBundle(
                "The ZIP bundle could not be read",
                exception
            );
        }
    }

    private static String selectMarkdown(Map<String, ZipEntry> files) {
        String prefix = commonTopLevelDirectory(files.keySet());
        String preferred = prefix + PREFERRED_DOCUMENT;
        if (files.containsKey(preferred)) {
            return preferred;
        }
        List<String> candidates = new ArrayList<>();
        for (String name : files.keySet()) {
            String relative = name.substring(prefix.length());
            if (!relative.contains("/") && isMarkdownName(relative)) {
                candidates.add(name);
            }
        }
        if (candidates.isEmpty()) {
            throw new OperationException(
                "MARKDOWN_BUNDLE_MISSING_DOCUMENT",
                "The ZIP bundle must contain document.md or one top-level Markdown file"
            );
        }
        if (candidates.size() > 1) {
            throw new OperationException(
                "MARKDOWN_BUNDLE_AMBIGUOUS_DOCUMENT",
                "The ZIP bundle contains several top-level Markdown files; name the main one document.md"
            );
        }
        return candidates.getFirst();
    }

    /**
     * Zipping a folder usually nests everything under one directory; treat
     * that directory as the bundle root.
     */
    private static String commonTopLevelDirectory(Iterable<String> names) {
        String common = null;
        for (String name : names) {
            int separator = name.indexOf('/');
            if (separator < 0) {
                return "";
            }
            String top = name.substring(0, separator + 1);
            if (common == null) {
                common = top;
            } else if (!common.equals(top)) {
                return "";
            }
        }
        return common == null ? "" : common;
    }

    private static boolean isMarkdownName(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".md") || lower.endsWith(".markdown");
    }

    private static boolean ignored(String name) {
        return name.startsWith("__MACOSX/")
            || name.equals(".DS_Store")
            || name.endsWith("/.DS_Store");
    }

    private static void requireSafeName(String name) {
        if (name.isEmpty()
                || name.length() > 1024
                || name.indexOf('\\') >= 0
                || name.indexOf('\u0000') >= 0
                || name.startsWith("/")
                || name.matches("^[A-Za-z]:.*")) {
            throw unsafePath();
        }
        String body = name.endsWith("/")
            ? name.substring(0, name.length() - 1)
            : name;
        for (String segment : body.split("/", -1)) {
            if (segment.isEmpty()
                    || segment.equals(".")
                    || segment.equals("..")) {
                throw unsafePath();
            }
        }
    }

    private static OperationException unsafePath() {
        return invalidBundle(
            "The ZIP bundle contains an unsafe entry path",
            null
        );
    }

    private static OperationException invalidBundle(
            String message,
            Throwable cause) {
        return new OperationException(
            "INVALID_MARKDOWN_BUNDLE",
            message,
            cause
        );
    }

    private static void closeQuietly(ZipFile zip, RuntimeException failure) {
        try {
            zip.close();
        } catch (IOException exception) {
            failure.addSuppressed(exception);
        }
    }
}
