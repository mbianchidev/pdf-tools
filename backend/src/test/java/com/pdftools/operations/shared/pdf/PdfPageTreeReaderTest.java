package com.pdftools.operations.shared.pdf;

import com.pdftools.operations.split.SplitProperties;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class PdfPageTreeReaderTest {

    @Test
    void normalizesIntermediateCountsBeforeIndexedRendering()
            throws Exception {
        try (PDDocument document = new PDDocument()) {
            PDPage first = new PDPage();
            PDPage second = new PDPage();
            document.addPage(first);
            document.addPage(second);
            COSDictionary root = document.getPages().getCOSObject();
            COSDictionary firstBranch = branch(root, first, 1000);
            COSDictionary secondBranch = branch(root, second, 1);
            COSArray branches = new COSArray();
            branches.add(firstBranch);
            branches.add(secondBranch);
            root.setItem(COSName.KIDS, branches);
            root.setInt(COSName.COUNT, 2);

            new PdfPageTreeReader(new SplitProperties()).read(
                document,
                () -> {
                }
            );

            assertEquals(1, firstBranch.getInt(COSName.COUNT));
            assertSame(
                second.getCOSObject(),
                document.getPage(1).getCOSObject()
            );
        }
    }

    private COSDictionary branch(
            COSDictionary parent,
            PDPage page,
            int count) {
        COSDictionary branch = new COSDictionary();
        branch.setItem(COSName.TYPE, COSName.PAGES);
        branch.setItem(COSName.PARENT, parent);
        COSArray kids = new COSArray();
        kids.add(page.getCOSObject());
        branch.setItem(COSName.KIDS, kids);
        branch.setInt(COSName.COUNT, count);
        page.getCOSObject().setItem(COSName.PARENT, branch);
        return branch;
    }
}
