package com.pdftools.operations.split;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

final class SplitBudgetOutputStream extends FilterOutputStream {

    private final SplitDecodedBudget budget;

    SplitBudgetOutputStream(
            OutputStream output,
            SplitDecodedBudget budget) {
        super(output);
        this.budget = budget;
    }

    @Override
    public void write(int value) throws IOException {
        budget.consume(1);
        out.write(value);
    }

    @Override
    public void write(byte[] bytes, int offset, int length)
            throws IOException {
        budget.consume(length);
        out.write(bytes, offset, length);
    }
}
