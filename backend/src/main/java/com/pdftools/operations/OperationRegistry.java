package com.pdftools.operations;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Component
public class OperationRegistry {

    private final Map<String, PdfOperation> operations;

    public OperationRegistry(List<PdfOperation> registeredOperations) {
        Map<String, PdfOperation> operationMap = new LinkedHashMap<>();
        for (PdfOperation operation : registeredOperations) {
            PdfOperation previous = operationMap.put(operation.key(), operation);
            if (previous != null) {
                throw new IllegalStateException("Duplicate PDF operation key: " + operation.key());
            }
        }
        this.operations = Map.copyOf(operationMap);
    }

    public Optional<PdfOperation> find(String key) {
        return Optional.ofNullable(operations.get(key));
    }

    public Set<String> keys() {
        return operations.keySet();
    }
}
