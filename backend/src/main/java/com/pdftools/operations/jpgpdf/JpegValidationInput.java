package com.pdftools.operations.jpgpdf;

import java.nio.file.Path;

record JpegValidationInput(
    Path source,
    JpegInspector.JpegInfo info
) {
}
