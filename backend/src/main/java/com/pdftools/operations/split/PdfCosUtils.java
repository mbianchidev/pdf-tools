package com.pdftools.operations.split;

import com.pdftools.operations.OperationException;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.pdmodel.common.PDRectangle;

import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

final class PdfCosUtils {

    private static final int MAX_INDIRECT_REFERENCE_DEPTH = 64;

    private PdfCosUtils() {
    }

    static COSBase dereference(COSBase value) {
        COSBase resolved = value;
        Set<COSObject> seen =
            Collections.newSetFromMap(new IdentityHashMap<>());
        int depth = 0;
        while (resolved instanceof COSObject object) {
            if (!seen.add(object)) {
                throw new OperationException(
                    "CYCLIC_PDF_REFERENCE",
                    "The PDF contains cyclic indirect references"
                );
            }
            if (++depth > MAX_INDIRECT_REFERENCE_DEPTH) {
                throw new OperationException(
                    "PDF_REFERENCE_DEPTH_LIMIT_EXCEEDED",
                    "The PDF indirect-reference chain is too deep"
                );
            }
            resolved = object.getObject();
        }
        return resolved;
    }

    static List<COSName> sortedNames(Set<COSName> names) {
        return names.stream()
            .sorted(Comparator.comparing(COSName::getName))
            .toList();
    }

    static OperationException unsupportedTransparencyGroup() {
        return new OperationException(
            "UNSUPPORTED_TRANSPARENCY_GROUP",
            "PDF transparency group metadata is not supported"
        );
    }

    static PDRectangle copyRectangle(PDRectangle source) {
        if (source == null) {
            return null;
        }
        return new PDRectangle(
            source.getLowerLeftX(),
            source.getLowerLeftY(),
            source.getWidth(),
            source.getHeight()
        );
    }
}
