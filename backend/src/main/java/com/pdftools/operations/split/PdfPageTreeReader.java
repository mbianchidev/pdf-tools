package com.pdftools.operations.split;

import com.pdftools.operations.OperationException;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class PdfPageTreeReader {

    private static final int CANCELLATION_INTERVAL = 25;

    private final SplitProperties properties;

    PdfPageTreeReader(SplitProperties properties) {
        this.properties = properties;
    }

    Result read(PDDocument source, Runnable cancellationCheck) {
        COSDictionary root = source.getDocumentCatalog()
            .getCOSObject()
            .getCOSDictionary(COSName.PAGES);
        if (root == null) {
            throw invalidPageTree("The PDF page tree is missing");
        }

        List<PDPage> pages = new ArrayList<>();
        Set<COSDictionary> visited =
            Collections.newSetFromMap(new IdentityHashMap<>());
        ArrayDeque<PageTreeEntry> pending = new ArrayDeque<>();
        pending.push(new PageTreeEntry(root, null, 0));
        int nodes = 0;
        while (!pending.isEmpty()) {
            PageTreeEntry entry = pending.pop();
            COSDictionary node = entry.node();
            nodes++;
            enforceTreeComplexity(nodes, entry.depth());
            if (nodes % CANCELLATION_INTERVAL == 0) {
                cancellationCheck.run();
            }
            if (!visited.add(node)) {
                throw invalidPageTree(
                    "The PDF page tree contains a cycle or duplicate node"
                );
            }
            COSBase actualParent = PdfCosUtils.dereference(
                node.getItem(COSName.PARENT)
            );
            if (actualParent != entry.parent()) {
                throw invalidPageTree(
                    "The PDF page tree contains an invalid parent link"
                );
            }

            COSBase type = PdfCosUtils.dereference(
                node.getItem(COSName.TYPE)
            );
            if (COSName.PAGE.equals(type)) {
                addPage(pages, node);
                continue;
            }
            if (!COSName.PAGES.equals(type)) {
                throw invalidPageTree(
                    "The PDF page tree contains an invalid node type"
                );
            }
            COSBase kidsValue = PdfCosUtils.dereference(
                node.getItem(COSName.KIDS)
            );
            if (!(kidsValue instanceof COSArray kids)) {
                throw invalidPageTree(
                    "The PDF page tree node does not contain a valid Kids array"
                );
            }
            if ((long) nodes + kids.size() > properties.getMaxResourceNodes()) {
                throw pageTreeComplexity();
            }
            for (int index = kids.size() - 1; index >= 0; index--) {
                COSBase child = PdfCosUtils.dereference(kids.get(index));
                if (!(child instanceof COSDictionary childDictionary)) {
                    throw invalidPageTree(
                        "The PDF page tree contains a non-dictionary child"
                    );
                }
                pending.push(new PageTreeEntry(
                    childDictionary,
                    node,
                    entry.depth() + 1
                ));
            }
        }

        validatePageContents(pages, cancellationCheck);
        cancellationCheck.run();
        return new Result(
            List.copyOf(pages),
            pageLocalReferences(pages)
        );
    }

    private void addPage(List<PDPage> pages, COSDictionary node) {
        if (pages.size() >= properties.getMaxPages()) {
            throw new OperationException(
                "PDF_PAGE_LIMIT_EXCEEDED",
                "The PDF exceeds the split page limit",
                Map.of("maxPages", properties.getMaxPages())
            );
        }
        pages.add(new PDPage(node));
    }

    private void enforceTreeComplexity(int nodes, int depth) {
        if (nodes > properties.getMaxResourceNodes()
                || depth > properties.getMaxResourceDepth()) {
            throw pageTreeComplexity();
        }
    }

    private OperationException pageTreeComplexity() {
        return new OperationException(
            "PDF_PAGE_TREE_COMPLEXITY_LIMIT_EXCEEDED",
            "The PDF page tree exceeds the configured complexity limit"
        );
    }

    private OperationException invalidPageTree(String message) {
        return new OperationException("INVALID_PDF_PAGE_TREE", message);
    }

    private void validatePageContents(
            List<PDPage> pages,
            Runnable cancellationCheck) {
        for (int pageIndex = 0; pageIndex < pages.size(); pageIndex++) {
            if (pageIndex % CANCELLATION_INTERVAL == 0) {
                cancellationCheck.run();
            }
            COSBase contents = PdfCosUtils.dereference(
                pages.get(pageIndex).getCOSObject().getItem(COSName.CONTENTS)
            );
            if (contents == null || contents instanceof COSStream) {
                continue;
            }
            if (!(contents instanceof COSArray streams)) {
                throw new OperationException(
                    "INVALID_PDF_CONTENT",
                    "A page contains an invalid Contents entry"
                );
            }
            if (streams.size() > properties.getMaxContentStreamsPerPage()) {
                throw new OperationException(
                    "PDF_CONTENT_STREAM_LIMIT_EXCEEDED",
                    "A page contains too many content streams",
                    Map.of(
                        "page", pageIndex + 1,
                        "maxContentStreamsPerPage",
                        properties.getMaxContentStreamsPerPage()
                    )
                );
            }
            for (int index = 0; index < streams.size(); index++) {
                if (index % CANCELLATION_INTERVAL == 0) {
                    cancellationCheck.run();
                }
                if (!(PdfCosUtils.dereference(
                        streams.get(index)) instanceof COSStream)) {
                    throw new OperationException(
                        "INVALID_PDF_CONTENT",
                        "A page contains a non-stream Contents entry"
                    );
                }
            }
        }
    }

    private Set<COSBase> pageLocalReferences(List<PDPage> pages) {
        Set<COSBase> references =
            Collections.newSetFromMap(new IdentityHashMap<>());
        for (PDPage page : pages) {
            references.add(page.getCOSObject());
            COSBase contents = PdfCosUtils.dereference(
                page.getCOSObject().getItem(COSName.CONTENTS)
            );
            if (contents instanceof COSArray streams) {
                for (int index = 0; index < streams.size(); index++) {
                    COSBase stream = PdfCosUtils.dereference(
                        streams.get(index)
                    );
                    if (stream != null) {
                        references.add(stream);
                    }
                }
            } else if (contents != null) {
                references.add(contents);
            }
        }
        return Collections.unmodifiableSet(references);
    }

    record Result(List<PDPage> pages, Set<COSBase> pageLocalReferences) {
    }

    private record PageTreeEntry(
        COSDictionary node,
        COSDictionary parent,
        int depth
    ) {
    }
}
