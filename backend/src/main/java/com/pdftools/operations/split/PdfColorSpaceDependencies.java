package com.pdftools.operations.split;

import com.pdftools.operations.shared.pdf.PdfCosUtils;
import com.pdftools.operations.OperationException;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSNull;
import org.apache.pdfbox.pdmodel.PDResources;

import java.io.IOException;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

final class PdfColorSpaceDependencies {

    private static final Set<COSName> BUILT_IN_COLOR_SPACES = Set.of(
        COSName.getPDFName("DeviceGray"),
        COSName.getPDFName("DeviceRGB"),
        COSName.getPDFName("DeviceCMYK"),
        COSName.getPDFName("G"),
        COSName.getPDFName("RGB"),
        COSName.getPDFName("CMYK"),
        COSName.getPDFName("CalGray"),
        COSName.getPDFName("CalRGB"),
        COSName.getPDFName("Lab"),
        COSName.getPDFName("ICCBased"),
        COSName.getPDFName("Indexed"),
        COSName.getPDFName("I"),
        COSName.getPDFName("Separation"),
        COSName.getPDFName("DeviceN"),
        COSName.getPDFName("NChannel"),
        COSName.getPDFName("Pattern")
    );

    Set<COSName> find(
            COSBase value,
            PDResources resources,
            PdfResourceTraversal traversal) throws IOException {
        Set<COSName> names = new HashSet<>();
        collect(value, resources, names, traversal);
        return names;
    }

    private void collect(
            COSBase value,
            PDResources resources,
            Set<COSName> names,
            PdfResourceTraversal traversal) throws IOException {
        COSBase resolved = PdfCosUtils.dereference(value);
        if (resolved == null || resolved == COSNull.NULL) {
            return;
        }
        traversal.enter(resolved);
        try {
            if (resolved instanceof COSName name) {
                if (!BUILT_IN_COLOR_SPACES.contains(name)
                        && namedColorSpace(resources, name) != null) {
                    names.add(name);
                }
                return;
            }
            if (!(resolved instanceof COSArray array)
                    || array.size() == 0) {
                throw invalid("A color space has an invalid structure");
            }
            COSBase familyValue = PdfCosUtils.dereference(array.get(0));
            if (!(familyValue instanceof COSName family)) {
                throw invalid("A color-space array has no valid family");
            }
            switch (family.getName()) {
                case "Indexed", "I" ->
                    collectAt(array, 1, resources, names, traversal);
                case "Separation" ->
                    collectAt(array, 2, resources, names, traversal);
                case "DeviceN", "NChannel" -> {
                    collectAt(array, 2, resources, names, traversal);
                    collectDeviceN(array, resources, names, traversal);
                }
                case "Pattern" -> {
                    if (array.size() > 1) {
                        collectAt(array, 1, resources, names, traversal);
                    }
                }
                case "ICCBased" ->
                    collectIccAlternate(array, resources, names, traversal);
                default -> {
                }
            }
        } finally {
            traversal.exit(resolved);
        }
    }

    private void collectAt(
            COSArray array,
            int index,
            PDResources resources,
            Set<COSName> names,
            PdfResourceTraversal traversal) throws IOException {
        if (array.size() <= index) {
            throw invalid("A color-space array is missing a required entry");
        }
        collect(array.get(index), resources, names, traversal);
    }

    private void collectIccAlternate(
            COSArray array,
            PDResources resources,
            Set<COSName> names,
            PdfResourceTraversal traversal) throws IOException {
        if (array.size() < 2) {
            throw invalid("An ICC color space is missing its profile");
        }
        COSBase profile = PdfCosUtils.dereference(array.get(1));
        if (!(profile instanceof COSDictionary dictionary)) {
            throw invalid("An ICC color space has an invalid profile");
        }
        COSName alternate = COSName.getPDFName("Alternate");
        if (dictionary.containsKey(alternate)) {
            collect(
                dictionary.getItem(alternate),
                resources,
                names,
                traversal
            );
        }
    }

    private void collectDeviceN(
            COSArray array,
            PDResources resources,
            Set<COSName> names,
            PdfResourceTraversal traversal) throws IOException {
        if (array.size() < 5) {
            return;
        }
        COSBase attributes = PdfCosUtils.dereference(array.get(4));
        if (!(attributes instanceof COSDictionary dictionary)) {
            return;
        }
        COSDictionary process = dictionary.getCOSDictionary(
            COSName.getPDFName("Process")
        );
        if (process != null && process.containsKey(COSName.COLORSPACE)) {
            collect(
                process.getItem(COSName.COLORSPACE),
                resources,
                names,
                traversal
            );
        }
        COSDictionary colorants = dictionary.getCOSDictionary(
            COSName.getPDFName("Colorants")
        );
        if (colorants == null) {
            return;
        }
        for (COSName colorant : colorants.keySet().stream()
                .sorted(Comparator.comparing(COSName::getName))
                .toList()) {
            collect(
                colorants.getItem(colorant),
                resources,
                names,
                traversal
            );
        }
    }

    private COSBase namedColorSpace(
            PDResources resources,
            COSName name) {
        COSDictionary colorSpaces = resources.getCOSObject()
            .getCOSDictionary(COSName.COLORSPACE);
        return colorSpaces == null ? null : colorSpaces.getItem(name);
    }

    private OperationException invalid(String message) {
        return new OperationException("INVALID_PDF_RESOURCE", message);
    }
}
