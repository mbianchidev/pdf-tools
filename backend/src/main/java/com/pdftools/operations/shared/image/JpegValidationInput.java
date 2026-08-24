package com.pdftools.operations.shared.image;

import java.nio.file.Path;

public record JpegValidationInput(
    Path source,
    JpegInspector.JpegInfo info
) {
}
