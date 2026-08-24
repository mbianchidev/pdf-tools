package com.pdftools.operations.shared.image;

import com.pdftools.operations.OperationException;

import java.util.function.Supplier;

public final class JpegResourceGuard {

    private JpegResourceGuard() {
    }

    public static void enforce(
            JpegInspector.JpegInfo info,
            int maxDimension,
            long maxPixels,
            long maxProgressiveCoefficientBytes,
            Supplier<OperationException> dimensionFailure,
            Supplier<OperationException> progressiveFailure) {
        long pixels;
        try {
            pixels = Math.multiplyExact(
                (long) info.width(),
                info.height()
            );
        } catch (ArithmeticException exception) {
            throw dimensionFailure.get();
        }
        if (info.width() < 1
                || info.height() < 1
                || info.width() > maxDimension
                || info.height() > maxDimension
                || pixels > maxPixels) {
            throw dimensionFailure.get();
        }
        if (!info.progressive()) {
            return;
        }
        try {
            long blocksWide = ((long) info.width() + 7) / 8;
            long blocksHigh = ((long) info.height() + 7) / 8;
            long bytes = Math.multiplyExact(
                Math.multiplyExact(blocksWide, blocksHigh),
                128L * info.components()
            );
            if (bytes > maxProgressiveCoefficientBytes) {
                throw progressiveFailure.get();
            }
        } catch (ArithmeticException exception) {
            throw progressiveFailure.get();
        }
    }
}
