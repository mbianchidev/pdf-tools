package com.pdftools.operations.split;

import com.pdftools.operations.shared.pdf.PdfCosUtils;
import com.pdftools.operations.CheckpointInputStream;
import com.pdftools.operations.OperationException;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSNull;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.pdmodel.PDDocument;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class PdfResourceCopier {

    private final PDDocument destination;
    private final Runnable cancellationCheck;
    private final Set<COSBase> forbiddenReferences;
    private final SplitDecodedBudget scratchBudget;
    private final int maxDepth;
    private final int maxNodes;
    private final SplitStructureBudget structureBudget;
    private final Map<COSBase, COSBase> clones = new IdentityHashMap<>();
    private final Map<COSDictionary, Map<List<COSName>, COSDictionary>>
        selectedClones = new IdentityHashMap<>();

    PdfResourceCopier(
            PDDocument destination,
            SplitProperties properties,
            Runnable cancellationCheck,
            Set<COSBase> forbiddenReferences,
            SplitDecodedBudget scratchBudget,
            SplitStructureBudget structureBudget) {
        this.destination = destination;
        this.cancellationCheck = cancellationCheck;
        this.forbiddenReferences = forbiddenReferences;
        this.scratchBudget = scratchBudget;
        this.maxDepth = properties.getMaxResourceDepth();
        this.maxNodes = properties.getMaxResourceNodes();
        this.structureBudget = structureBudget;
    }

    COSBase cloneResource(COSBase value) throws IOException {
        return cloneResource(value, newTraversal());
    }

    private COSBase cloneResource(
            COSBase value,
            PdfResourceTraversal traversal) throws IOException {
        COSBase source = PdfCosUtils.dereference(value);
        if (source == null) {
            return COSNull.NULL;
        }
        validateReference(source);

        traversal.enter(source);
        try {
            COSBase cached = clones.get(source);
            if (cached != null) {
                return cached;
            }
            if (source instanceof COSString string) {
                byte[] bytes = string.getBytes();
                scratchBudget.consume(bytes.length);
                COSString clone = new COSString(bytes);
                clones.put(source, clone);
                return clone;
            }
            if (source instanceof COSStream stream) {
                return cloneStream(stream, traversal);
            }
            if (source instanceof COSDictionary dictionary) {
                COSDictionary clone = new COSDictionary();
                clones.put(source, clone);
                copyEntries(dictionary, clone, false, traversal);
                return clone;
            }
            if (source instanceof COSArray array) {
                COSArray clone = new COSArray();
                clones.put(source, clone);
                for (int index = 0; index < array.size(); index++) {
                    clone.add(cloneResource(array.get(index), traversal));
                }
                return clone;
            }
            return source;
        } finally {
            traversal.exit(source);
        }
    }

    COSDictionary cloneSelectedDictionary(
            COSDictionary source,
            List<COSName> keys) throws IOException {
        return cloneSelectedDictionary(source, keys, newTraversal());
    }

    private COSDictionary cloneSelectedDictionary(
            COSDictionary source,
            List<COSName> keys,
            PdfResourceTraversal traversal) throws IOException {
        validateReference(source);
        COSDictionary cached = selectedClones
            .computeIfAbsent(source, ignored -> new java.util.HashMap<>())
            .get(keys);
        if (cached != null) {
            return cached;
        }
        traversal.enter(source);
        COSDictionary clone = source instanceof COSStream
            ? destination.getDocument().createCOSStream()
            : new COSDictionary();
        selectedClones.get(source).put(keys, clone);
        try {
            for (COSName key : keys) {
                if (!COSName.LENGTH.equals(key)
                        && source.containsKey(key)) {
                    clone.setItem(
                        key,
                        cloneResource(source.getItem(key), traversal)
                    );
                }
            }
            if (source instanceof COSStream sourceStream
                    && clone instanceof COSStream cloneStream) {
                try (InputStream input = new CheckpointInputStream(
                        sourceStream.createRawInputStream(),
                        cancellationCheck);
                     OutputStream output = cloneStream.createRawOutputStream();
                     OutputStream budgeted = new SplitBudgetOutputStream(
                         output,
                         scratchBudget
                     )) {
                    input.transferTo(budgeted);
                }
            }
            return clone;
        } catch (IOException | RuntimeException exception) {
            selectedClones.get(source).remove(keys);
            if (clone instanceof COSStream stream) {
                stream.close();
            }
            throw exception;
        } finally {
            traversal.exit(source);
        }
    }

    private COSStream cloneStream(
            COSStream source,
            PdfResourceTraversal traversal) throws IOException {
        COSStream clone = destination.getDocument().createCOSStream();
        clones.put(source, clone);
        copyEntries(source, clone, true, traversal);
        try (InputStream input = new CheckpointInputStream(
                source.createRawInputStream(),
                cancellationCheck);
             OutputStream output = clone.createRawOutputStream();
             OutputStream budgeted = new SplitBudgetOutputStream(
                 output,
                 scratchBudget
             )) {
            input.transferTo(budgeted);
        }
        cancellationCheck.run();
        return clone;
    }

    private void copyEntries(
            COSDictionary source,
            COSDictionary destinationDictionary,
            boolean skipLength,
            PdfResourceTraversal traversal) throws IOException {
        for (COSName key : source.keySet()) {
            if (!skipLength || !COSName.LENGTH.equals(key)) {
                destinationDictionary.setItem(
                    key,
                    cloneResource(source.getItem(key), traversal)
                );
            }
        }
    }

    private void validateReference(COSBase value) {
        if (forbiddenReferences.contains(value)
                || isPageTreeDictionary(value)) {
            throw new OperationException(
                "UNSAFE_RESOURCE_REFERENCE",
                "A PDF resource references page-local document content"
            );
        }

    }

    private PdfResourceTraversal newTraversal() {
        return new PdfResourceTraversal(
            maxDepth,
            maxNodes,
            cancellationCheck,
            structureBudget
        );
    }

    private boolean isPageTreeDictionary(COSBase value) {
        if (!(value instanceof COSDictionary dictionary)) {
            return false;
        }
        COSBase type = PdfCosUtils.dereference(
            dictionary.getItem(COSName.TYPE)
        );
        return COSName.PAGE.equals(type)
            || COSName.PAGES.equals(type)
            || COSName.CATALOG.equals(type);
    }
}
