package com.pdftools.operations.split;

import com.pdftools.operations.OperationException;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType3Font;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;

final class PdfFontValidator {

    private PdfFontValidator() {
    }

    static void rejectType3(PDFont font) {
        if (font instanceof PDType3Font) {
            throw unsupportedType3();
        }
    }

    static void rejectType3(PDExtendedGraphicsState state) {
        COSBase rawSetting = PdfCosUtils.dereference(
            state.getCOSObject().getItem(COSName.FONT)
        );
        if (rawSetting == null) {
            return;
        }
        if (!(rawSetting instanceof COSArray fontSetting)
                || fontSetting.size() == 0) {
            throw invalidFont(
                "An extended graphics state has an invalid font setting"
            );
        }
        rejectType3Dictionary(fontSetting.get(0));
    }

    static void rejectType3Dictionary(COSBase value) {
        COSBase font = PdfCosUtils.dereference(value);
        if (!(font instanceof COSDictionary dictionary)) {
            throw invalidFont("A PDF font resource has an invalid structure");
        }
        COSBase subtype = PdfCosUtils.dereference(
            dictionary.getItem(COSName.SUBTYPE)
        );
        if (COSName.TYPE3.equals(subtype)) {
            throw unsupportedType3();
        }
    }

    private static OperationException unsupportedType3() {
        return new OperationException(
            "UNSUPPORTED_TYPE3_FONT",
            "Split does not support Type 3 fonts"
        );
    }

    private static OperationException invalidFont(String message) {
        return new OperationException("INVALID_PDF_RESOURCE", message);
    }
}
