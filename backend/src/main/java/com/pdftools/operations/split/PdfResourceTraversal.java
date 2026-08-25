package com.pdftools.operations.split;

import com.pdftools.operations.OperationException;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

final class PdfResourceTraversal {

    private final int maxDepth;
    private final int maxNodes;
    private final Runnable cancellationCheck;
    private final SplitStructureBudget totalBudget;
    private final Set<Object> stack =
        Collections.newSetFromMap(new IdentityHashMap<>());
    private int nodes;

    PdfResourceTraversal(
            int maxDepth,
            int maxNodes,
            Runnable cancellationCheck,
            SplitStructureBudget totalBudget) {
        this.maxDepth = maxDepth;
        this.maxNodes = maxNodes;
        this.cancellationCheck = cancellationCheck;
        this.totalBudget = totalBudget;
    }

    void enter(Object resource) {
        nodes++;
        totalBudget.consumeNode();
        if (nodes % 256 == 0) {
            cancellationCheck.run();
        }
        if (nodes > maxNodes || stack.size() >= maxDepth) {
            throw new OperationException(
                "PDF_RESOURCE_COMPLEXITY_LIMIT_EXCEEDED",
                "PDF resource graph exceeds the configured complexity limit"
            );
        }
        if (!stack.add(resource)) {
            throw new OperationException(
                "CYCLIC_PDF_RESOURCE",
                "The PDF contains a cyclic resource graph"
            );
        }
    }

    void exit(Object resource) {
        stack.remove(resource);
    }

    boolean isActive(Object resource) {
        return stack.contains(resource);
    }
}
