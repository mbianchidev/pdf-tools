package com.pdftools.operations.split;

import com.pdftools.operations.OperationException;

final class SplitStructureBudget {

    private int remainingNodes;

    SplitStructureBudget(int maxNodes) {
        this.remainingNodes = maxNodes;
    }

    void consumeNode() {
        if (remainingNodes < 1) {
            throw new OperationException(
                "PDF_RESOURCE_TOTAL_COMPLEXITY_LIMIT_EXCEEDED",
                "Split resource structures exceed the job-wide complexity limit"
            );
        }
        remainingNodes--;
    }
}
