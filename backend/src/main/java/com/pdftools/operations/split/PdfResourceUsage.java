package com.pdftools.operations.split;

import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSName;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

record PdfResourceUsage(
    Set<COSName> fonts,
    Set<COSName> xObjects,
    Set<COSName> extendedGraphicsStates,
    Set<COSName> colorSpaces,
    Set<COSName> shadings,
    Set<COSName> patterns,
    Set<COSName> properties,
    List<COSBase> inlineColorSpaces
) {
    PdfResourceUsage() {
        this(
            new HashSet<>(),
            new HashSet<>(),
            new HashSet<>(),
            new HashSet<>(),
            new HashSet<>(),
            new HashSet<>(),
            new HashSet<>(),
            new ArrayList<>()
        );
    }
}
