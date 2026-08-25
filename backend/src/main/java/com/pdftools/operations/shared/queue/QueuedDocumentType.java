package com.pdftools.operations.shared.queue;

public interface QueuedDocumentType {

    String key();

    String code(String suffix);

    String label();

    String outputFilename();

    String invalidPdfCode();

    String extension(String filename);

    default int maxPages() {
        return Integer.MAX_VALUE;
    }
}
