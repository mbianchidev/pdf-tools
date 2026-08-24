package com.pdftools.operations;

import java.io.IOException;

public class OutputLimitExceededException extends IOException {

    public OutputLimitExceededException(long maxBytes) {
        super("Output exceeds the " + maxBytes + "-byte limit");
    }
}
