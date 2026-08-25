package com.pdftools.operations.htmlpdf;

import com.pdftools.operations.shared.queue.QueuedDocumentType;

import java.util.Locale;

record HtmlDocumentType(int maxPages) implements QueuedDocumentType {

    @Override
    public String key() {
        return "html";
    }

    @Override
    public String code(String suffix) {
        return "HTML_" + suffix;
    }

    @Override
    public String label() {
        return "HTML document";
    }

    @Override
    public String outputFilename() {
        return "html-to-pdf.pdf";
    }

    @Override
    public String invalidPdfCode() {
        return "INVALID_HTML_PDF_OUTPUT";
    }

    @Override
    public String extension(String filename) {
        return filename.toLowerCase(Locale.ROOT).endsWith(".html")
            ? ".html"
            : ".htm";
    }
}
