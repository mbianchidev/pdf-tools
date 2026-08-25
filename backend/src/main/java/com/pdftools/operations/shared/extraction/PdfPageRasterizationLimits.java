package com.pdftools.operations.shared.extraction;

public interface PdfPageRasterizationLimits
        extends PdfImageExtractionLimits {

    int getRenderDpi();

    long getMaxRenderPixelsPerPage();
}
