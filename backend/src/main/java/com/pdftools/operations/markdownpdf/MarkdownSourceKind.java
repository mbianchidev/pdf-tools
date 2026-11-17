package com.pdftools.operations.markdownpdf;

import com.pdftools.operations.OperationException;

import java.util.Locale;

enum MarkdownSourceKind {
    MARKDOWN,
    BUNDLE;

    static MarkdownSourceKind fromFilename(String filename) {
        String lower = filename == null
            ? ""
            : filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".md") || lower.endsWith(".markdown")) {
            return MARKDOWN;
        }
        if (lower.endsWith(".zip")) {
            return BUNDLE;
        }
        throw invalidFile();
    }

    static String stripExtension(String filename) {
        String lower = filename.toLowerCase(Locale.ROOT);
        for (String extension : new String[] {".markdown", ".md", ".zip"}) {
            if (lower.endsWith(extension)) {
                return filename.substring(
                    0,
                    filename.length() - extension.length()
                );
            }
        }
        return filename;
    }

    static OperationException invalidFile() {
        return new OperationException(
            "INVALID_MARKDOWN_FILE",
            "Markdown to PDF accepts MD, MARKDOWN, and ZIP bundle files"
        );
    }
}
