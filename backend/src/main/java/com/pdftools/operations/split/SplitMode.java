package com.pdftools.operations.split;

import com.pdftools.operations.OperationException;

import java.util.Locale;

public enum SplitMode {
    INDIVIDUAL,
    RANGES,
    FIXED;

    public static SplitMode parse(String value) {
        try {
            return SplitMode.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new OperationException(
                "INVALID_SPLIT_MODE",
                "Split mode must be individual, ranges, or fixed"
            );
        }
    }
}
