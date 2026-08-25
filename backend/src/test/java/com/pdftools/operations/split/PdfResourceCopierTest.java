package com.pdftools.operations.split;

import com.pdftools.operations.OperationException;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PdfResourceCopierTest {

    @Test
    void cachesAndBudgetsSharedStringsByIdentity() throws Exception {
        COSString shared = new COSString("shared");
        COSArray source = new COSArray();
        for (int index = 0; index < 100; index++) {
            source.add(shared);
        }
        SplitProperties properties = new SplitProperties();
        SplitDecodedBudget budget = new SplitDecodedBudget(6);
        try (PDDocument destination = new PDDocument()) {
            PdfResourceCopier copier = new PdfResourceCopier(
                destination,
                properties,
                () -> {
                },
                identitySet(),
                budget,
                new SplitStructureBudget(1_000)
            );

            COSArray clone = (COSArray) copier.cloneResource(source);

            assertEquals(0, budget.remainingBytes());
            assertSame(clone.get(0), clone.get(99));
        }

        try (PDDocument destination = new PDDocument()) {
            PdfResourceCopier copier = new PdfResourceCopier(
                destination,
                properties,
                () -> {
                },
                identitySet(),
                new SplitDecodedBudget(5),
                new SplitStructureBudget(1_000)
            );
            assertEquals(
                "SPLIT_DECODED_CONTENT_LIMIT_EXCEEDED",
                assertThrows(
                    OperationException.class,
                    () -> copier.cloneResource(source)
                ).getCode()
            );
        }
    }

    private Set<COSBase> identitySet() {
        return Collections.newSetFromMap(new IdentityHashMap<>());
    }
}
