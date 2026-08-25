package com.pdftools.operations.pdfword;

import com.pdftools.operations.BoundedOutputStream;
import com.pdftools.operations.OperationException;
import com.pdftools.operations.OutputLimitExceededException;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.Document;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFStyle;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTStyle;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STSectionMark;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STPageOrientation;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STStyleType;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class PdfToWordDocxWriter {

    private static final int DEFAULT_MARGIN_TWIPS = 720;
    private static final float MAX_WORD_PAGE_POINTS = 22 * 72;

    private final PdfToWordProperties properties;

    PdfToWordDocxWriter(PdfToWordProperties properties) {
        this.properties = properties;
    }

    void write(
            List<PdfToWordPage> pages,
            PdfToWordPlanFactory.PdfToWordPlan plan,
            Path output,
            Runnable progress) {
        try (XWPFDocument document = new XWPFDocument()) {
            document.getProperties().getCoreProperties()
                .setCreator("PDF Tools");
            document.getProperties().getCoreProperties()
                .setTitle("PDF to Word conversion");
            configureStyles(document);
            for (int pageIndex = 0;
                    pageIndex < pages.size();
                    pageIndex++) {
                PdfToWordPage page = pages.get(pageIndex);
                if (plan.mode().equals("editable")) {
                    writeEditablePage(document, page, plan, pageIndex);
                } else {
                    writeImages(document, page, pageIndex);
                }
                if (plan.preservePageBreaks()
                        && pageIndex < pages.size() - 1) {
                    addSectionBreak(
                        document,
                        page,
                        plan.mode().equals("visual")
                    );
                }
                progress.run();
            }
            configureSection(
                bodySection(document),
                plan.preservePageBreaks()
                    ? pages.getLast()
                    : pages.getFirst(),
                plan.mode().equals("visual")
            );
            writeDocument(document, output);
        } catch (OperationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new OperationException(
                "PDF_WORD_WRITE_FAILED",
                "The Word document could not be generated",
                exception
            );
        }
    }

    private void writeEditablePage(
            XWPFDocument document,
            PdfToWordPage page,
            PdfToWordPlanFactory.PdfToWordPlan plan,
            int pageIndex) throws Exception {
        float medianFont = medianFont(page.lines());
        List<PdfToWordPage.PageImage> images = plan.includeImages()
            ? page.images().stream()
                .sorted(java.util.Comparator.comparing(
                    PdfToWordPage.PageImage::top))
                .toList()
            : List.of();
        int imageIndex = 0;
        int index = 0;
        while (index < page.lines().size()) {
            while (imageIndex < images.size()
                    && images.get(imageIndex).top()
                        <= page.lines().get(index).top()) {
                writeImage(
                    document,
                    page,
                    images.get(imageIndex),
                    pageIndex,
                    imageIndex,
                    false
                );
                imageIndex++;
            }
            TableCandidate table = plan.detectTables()
                ? tableAt(page.lines(), index)
                : null;
            if (table != null) {
                writeTable(document, table, page);
                index += table.rows().size();
            } else {
                writeLine(
                    document,
                    page.lines().get(index),
                    page,
                    medianFont
                );
                index++;
            }
        }
        while (imageIndex < images.size()) {
            writeImage(
                document,
                page,
                images.get(imageIndex),
                pageIndex,
                imageIndex,
                false
            );
            imageIndex++;
        }
        if (page.lines().isEmpty() && page.images().isEmpty()) {
            document.createParagraph().createRun().setText(
                "[No extractable text or images were found on this page.]"
            );
        }
    }

    private void writeLine(
            XWPFDocument document,
            PdfToWordPage.TextLine line,
            PdfToWordPage page,
            float medianFont) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setIndentationLeft(indentTwips(line.left(), page));
        paragraph.setSpacingAfter(40);
        if (line.fontSize() >= medianFont * 1.4f
                && line.text().length() <= 120) {
            paragraph.setStyle("Heading1");
        }
        for (int index = 0; index < line.words().size(); index++) {
            PdfToWordPage.TextWord word = line.words().get(index);
            XWPFRun run = paragraph.createRun();
            run.setText((index == 0 ? "" : " ") + word.text());
            run.setFontSize(Math.max(
                6,
                Math.min(
                    72,
                    Math.round(
                        word.fontSize()
                            * page.userUnit()
                            * documentScale(page)
                    )
                )
            ));
            run.setBold(word.bold());
        }
    }

    private TableCandidate tableAt(
            List<PdfToWordPage.TextLine> lines,
            int start) {
        List<Cell> first = cells(lines.get(start));
        if (first.size() < 2
                || first.size() > properties.getMaxTableColumns()) {
            return null;
        }
        List<List<Cell>> rows = new ArrayList<>();
        rows.add(first);
        for (int index = start + 1; index < lines.size(); index++) {
            List<Cell> candidate = cells(lines.get(index));
            if (!aligned(first, candidate)) {
                break;
            }
            rows.add(candidate);
        }
        return rows.size() >= 2
            ? new TableCandidate(List.copyOf(rows))
            : null;
    }

    private List<Cell> cells(PdfToWordPage.TextLine line) {
        List<Cell> cells = new ArrayList<>();
        List<PdfToWordPage.TextWord> current = new ArrayList<>();
        PdfToWordPage.TextWord previous = null;
        for (PdfToWordPage.TextWord word : line.words()) {
            float gap = previous == null ? 0 : word.left() - previous.right();
            float threshold = Math.max(10, line.fontSize() * 0.65f);
            if (previous != null && gap > threshold) {
                cells.add(cell(current));
                current.clear();
            }
            current.add(word);
            previous = word;
        }
        if (!current.isEmpty()) {
            cells.add(cell(current));
        }
        return List.copyOf(cells);
    }

    private Cell cell(List<PdfToWordPage.TextWord> words) {
        return new Cell(
            words.getFirst().left(),
            words.stream().map(PdfToWordPage.TextWord::text)
                .reduce((left, right) -> left + " " + right)
                .orElse("")
        );
    }

    private boolean aligned(List<Cell> expected, List<Cell> actual) {
        if (expected.size() != actual.size()) {
            return false;
        }
        for (int index = 0; index < expected.size(); index++) {
            if (Math.abs(
                    expected.get(index).left()
                        - actual.get(index).left()) > 18) {
                return false;
            }
        }
        return true;
    }

    private void writeTable(
            XWPFDocument document,
            TableCandidate candidate,
            PdfToWordPage page) {
        int rowCount = candidate.rows().size();
        int columnCount = candidate.rows().getFirst().size();
        XWPFTable table = document.createTable();
        while (table.getNumberOfRows() > 0) {
            table.removeRow(0);
        }
        int tableWidth = contentWidthTwips(page);
        int cellWidth = Math.max(tableWidth / columnCount, 1);
        var grid = table.getCTTbl().getTblGrid();
        if (grid == null) {
            grid = table.getCTTbl().addNewTblGrid();
        }
        while (grid.sizeOfGridColArray() > 0) {
            grid.removeGridCol(0);
        }
        for (int index = 0; index < columnCount; index++) {
            grid.addNewGridCol().setW(BigInteger.valueOf(cellWidth));
        }
        table.setWidth(tableWidth);
        table.setInsideHBorder(
            XWPFTable.XWPFBorderType.SINGLE,
            2,
            0,
            "D7D7E0"
        );
        table.setInsideVBorder(
            XWPFTable.XWPFBorderType.SINGLE,
            2,
            0,
            "D7D7E0"
        );
        table.setTopBorder(
            XWPFTable.XWPFBorderType.SINGLE,
            2,
            0,
            "D7D7E0"
        );
        table.setBottomBorder(
            XWPFTable.XWPFBorderType.SINGLE,
            2,
            0,
            "D7D7E0"
        );
        table.setLeftBorder(
            XWPFTable.XWPFBorderType.SINGLE,
            2,
            0,
            "D7D7E0"
        );
        table.setRightBorder(
            XWPFTable.XWPFBorderType.SINGLE,
            2,
            0,
            "D7D7E0"
        );
        for (int rowIndex = 0; rowIndex < rowCount; rowIndex++) {
            XWPFTableRow row = table.createRow();
            while (row.getTableCells().size() < columnCount) {
                row.createCell();
            }
            for (int columnIndex = 0;
                    columnIndex < columnCount;
                    columnIndex++) {
                XWPFTableCell tableCell = row.getCell(columnIndex);
                tableCell.setWidth(Integer.toString(cellWidth));
                XWPFParagraph paragraph =
                    tableCell.getParagraphs().getFirst();
                XWPFRun run = paragraph.createRun();
                run.setText(
                    candidate.rows().get(rowIndex)
                        .get(columnIndex).text()
                );
                run.setBold(rowIndex == 0);
            }
        }
    }

    private void writeImages(
            XWPFDocument document,
            PdfToWordPage page,
            int pageIndex) throws Exception {
        for (int index = 0; index < page.images().size(); index++) {
            writeImage(
                document,
                page,
                page.images().get(index),
                pageIndex,
                index,
                true
            );
        }
    }

    private void writeImage(
            XWPFDocument document,
            PdfToWordPage page,
            PdfToWordPage.PageImage image,
            int pageIndex,
            int imageIndex,
            boolean visual) throws Exception {
        int contentWidth = Math.max(
            Math.round(
                page.width() * page.userUnit() * documentScale(page)
            )
                - 2 * marginPoints(visual),
            1
        );
        int contentHeight = Math.max(
            Math.round(
                page.height() * page.userUnit() * documentScale(page)
            )
                - 2 * marginPoints(visual),
            1
        );
        float scale = Math.min(
            1f,
            Math.min(
                contentWidth / Math.max(
                    image.width()
                        * page.userUnit()
                        * documentScale(page),
                    1f
                ),
                contentHeight / Math.max(
                    image.height()
                        * page.userUnit()
                        * documentScale(page),
                    1f
                )
            )
        );
        double width = Math.max(
            image.width()
                * page.userUnit()
                * documentScale(page)
                * scale,
            1
        );
        double height = Math.max(
            image.height()
                * page.userUnit()
                * documentScale(page)
                * scale,
            1
        );
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setIndentationLeft(indentTwips(image.left(), page));
        XWPFRun run = paragraph.createRun();
        try (ByteArrayInputStream input =
                 new ByteArrayInputStream(image.png())) {
            run.addPicture(
                input,
                Document.PICTURE_TYPE_PNG,
                "page-" + (pageIndex + 1)
                    + "-image-" + (imageIndex + 1) + ".png",
                Units.toEMU(width),
                Units.toEMU(height)
            );
        }
    }

    private void addSectionBreak(
            XWPFDocument document,
            PdfToWordPage page,
            boolean visual) {
        XWPFParagraph paragraph = document.createParagraph();
        var paragraphProperties = paragraph.getCTP().isSetPPr()
            ? paragraph.getCTP().getPPr()
            : paragraph.getCTP().addNewPPr();
        CTSectPr section = paragraphProperties.addNewSectPr();
        section.addNewType().setVal(STSectionMark.NEXT_PAGE);
        configureSection(section, page, visual);
    }

    private CTSectPr bodySection(XWPFDocument document) {
        return document.getDocument().getBody().isSetSectPr()
            ? document.getDocument().getBody().getSectPr()
            : document.getDocument().getBody().addNewSectPr();
    }

    private void configureSection(
            CTSectPr section,
            PdfToWordPage page,
            boolean visual) {
        CTPageSz size = section.isSetPgSz()
            ? section.getPgSz()
            : section.addNewPgSz();
        size.setW(BigInteger.valueOf(Math.round(
            page.width()
                * page.userUnit()
                * documentScale(page)
                * 20
        )));
        size.setH(BigInteger.valueOf(Math.round(
            page.height()
                * page.userUnit()
                * documentScale(page)
                * 20
        )));
        size.setOrient(
            page.width() > page.height()
                ? STPageOrientation.LANDSCAPE
                : STPageOrientation.PORTRAIT
        );
        CTPageMar margins = section.isSetPgMar()
            ? section.getPgMar()
            : section.addNewPgMar();
        long margin = visual ? 0 : DEFAULT_MARGIN_TWIPS;
        margins.setTop(BigInteger.valueOf(margin));
        margins.setRight(BigInteger.valueOf(margin));
        margins.setBottom(BigInteger.valueOf(margin));
        margins.setLeft(BigInteger.valueOf(margin));
        margins.setHeader(BigInteger.ZERO);
        margins.setFooter(BigInteger.ZERO);
        margins.setGutter(BigInteger.ZERO);
    }

    private void configureStyles(XWPFDocument document) {
        var styles = document.createStyles();
        CTStyle heading = CTStyle.Factory.newInstance();
        heading.setStyleId("Heading1");
        heading.setType(STStyleType.PARAGRAPH);
        heading.addNewName().setVal("Heading 1");
        heading.addNewQFormat();
        heading.addNewPPr().addNewOutlineLvl().setVal(BigInteger.ZERO);
        var runProperties = heading.addNewRPr();
        runProperties.addNewB();
        runProperties.addNewSz().setVal(BigInteger.valueOf(32));
        styles.addStyle(new XWPFStyle(heading));
    }

    private int indentTwips(float left, PdfToWordPage page) {
        int maximum = Math.max(contentWidthTwips(page) - 720, 0);
        return Math.max(
            0,
            Math.min(
                Math.round(
                    left
                        * page.userUnit()
                        * documentScale(page)
                        * 20
                ),
                maximum
            )
        );
    }

    private int contentWidthTwips(PdfToWordPage page) {
        return Math.max(
            Math.round(
                page.width()
                    * page.userUnit()
                    * documentScale(page)
                    * 20
            )
                - 2 * DEFAULT_MARGIN_TWIPS,
            1440
        );
    }

    private int marginPoints(boolean visual) {
        return visual ? 0 : DEFAULT_MARGIN_TWIPS / 20;
    }

    private float documentScale(PdfToWordPage page) {
        float physicalWidth = page.width() * page.userUnit();
        float physicalHeight = page.height() * page.userUnit();
        float longest = Math.max(physicalWidth, physicalHeight);
        return longest > MAX_WORD_PAGE_POINTS
            ? MAX_WORD_PAGE_POINTS / longest
            : 1f;
    }

    private float medianFont(List<PdfToWordPage.TextLine> lines) {
        if (lines.isEmpty()) {
            return 11;
        }
        List<Float> sizes = lines.stream()
            .map(PdfToWordPage.TextLine::fontSize)
            .sorted()
            .toList();
        return sizes.get(sizes.size() / 2);
    }

    private void writeDocument(XWPFDocument document, Path output)
            throws IOException {
        try (OutputStream fileOutput = Files.newOutputStream(output);
             BoundedOutputStream bounded = new BoundedOutputStream(
                 fileOutput,
                 properties.getMaxOutputBytes(),
                 () -> {
                 }
             )) {
            document.write(bounded);
        } catch (OutputLimitExceededException exception) {
            throw new OperationException(
                "PDF_WORD_OUTPUT_LIMIT_EXCEEDED",
                "The Word document exceeds the configured output limit",
                exception
            );
        }
    }

    private record Cell(float left, String text) {
    }

    private record TableCandidate(List<List<Cell>> rows) {
    }
}
