package com.pdftools.operations.shared.pdf;

public interface PdfPageTreeLimits {

    int maxPages();

    int maxPageTreeNodes();

    int maxPageTreeDepth();

    int maxContentStreamsPerPage();
}
