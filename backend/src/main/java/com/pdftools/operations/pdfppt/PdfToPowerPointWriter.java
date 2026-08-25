package com.pdftools.operations.pdfppt;

import com.pdftools.operations.BoundedOutputStream;
import com.pdftools.operations.OperationException;
import com.pdftools.operations.OutputLimitExceededException;
import com.pdftools.operations.shared.extraction.AlignedTableDetector;
import com.pdftools.operations.shared.extraction.AlignedTableDetector.TableCandidate;
import com.pdftools.operations.shared.extraction.PdfPageContent;
import org.apache.poi.sl.usermodel.PictureData;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFPictureData;
import org.apache.poi.xslf.usermodel.XSLFPictureShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTable;
import org.apache.poi.xslf.usermodel.XSLFTableCell;
import org.apache.poi.xslf.usermodel.XSLFTableRow;
import org.apache.poi.xslf.usermodel.XSLFTextBox;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

final class PdfToPowerPointWriter {

    private static final double MAX_SLIDE_POINTS = 56 * 72;

    private final PdfToPowerPointProperties properties;
    private final AlignedTableDetector tableDetector;

    PdfToPowerPointWriter(PdfToPowerPointProperties properties) {
        this.properties = properties;
        this.tableDetector = new AlignedTableDetector(
            properties.getMaxTableColumns()
        );
    }

    void write(
            List<PdfPageContent> pages,
            PdfToPowerPointPlanFactory.PdfToPowerPointPlan plan,
            Path output,
            Runnable progress) {
        Dimension slideSize = slideSize(pages.getFirst(), plan.slideSize());
        try (XMLSlideShow presentation = new XMLSlideShow()) {
            presentation.setPageSize(slideSize);
            int textBoxes = 0;
            for (int pageIndex = 0;
                    pageIndex < pages.size();
                    pageIndex++) {
                PdfPageContent page = pages.get(pageIndex);
                SlideTransform transform = SlideTransform.create(
                    page,
                    slideSize
                );
                XSLFSlide slide = presentation.createSlide();
                if (plan.mode().equals("visual")) {
                    writeImages(
                        presentation,
                        slide,
                        page,
                        transform
                    );
                } else {
                    textBoxes += writeEditable(
                        presentation,
                        slide,
                        page,
                        transform,
                        plan
                    );
                    if (textBoxes > properties.getMaxTextBoxes()) {
                        throw new OperationException(
                            "PDF_POWERPOINT_TEXT_BOX_LIMIT_EXCEEDED",
                            "The PDF exceeds the text box limit"
                        );
                    }
                }
                progress.run();
            }
            writePresentation(presentation, output);
        } catch (OperationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new OperationException(
                "PDF_POWERPOINT_WRITE_FAILED",
                "The PowerPoint presentation could not be generated",
                exception
            );
        }
    }

    private int writeEditable(
            XMLSlideShow presentation,
            XSLFSlide slide,
            PdfPageContent page,
            SlideTransform transform,
            PdfToPowerPointPlanFactory.PdfToPowerPointPlan plan)
            throws Exception {
        int textBoxes = 0;
        int lineIndex = 0;
        while (lineIndex < page.lines().size()) {
            TableCandidate table = plan.detectTables()
                ? tableDetector.tableAt(page.lines(), lineIndex)
                : null;
            if (table != null) {
                writeTable(
                    slide,
                    page,
                    transform,
                    lineIndex,
                    table
                );
                lineIndex += table.rows().size();
            } else {
                writeLine(
                    slide,
                    page.lines().get(lineIndex),
                    transform,
                    page.userUnit()
                );
                textBoxes++;
                lineIndex++;
            }
        }
        if (plan.includeImages()
                || (page.lines().isEmpty() && !page.images().isEmpty())) {
            writeImages(presentation, slide, page, transform);
        }
        return textBoxes;
    }

    private void writeLine(
            XSLFSlide slide,
            PdfPageContent.TextLine line,
            SlideTransform transform,
            float userUnit) {
        if (line.words().isEmpty()) {
            return;
        }
        float right = line.words().stream()
            .map(PdfPageContent.TextWord::right)
            .max(Float::compare)
            .orElse(line.left() + 1);
        float height = line.words().stream()
            .map(PdfPageContent.TextWord::height)
            .max(Float::compare)
            .orElse(line.fontSize());
        XSLFTextBox box = slide.createTextBox();
        box.setAnchor(transform.rectangle(
            line.left() * userUnit,
            line.top() * userUnit,
            Math.max((right - line.left()) * userUnit, 1),
            Math.max(height * userUnit * 1.35, 1)
        ));
        box.clearText();
        box.setWordWrap(false);
        box.setLeftInset(0);
        box.setRightInset(0);
        box.setTopInset(0);
        box.setBottomInset(0);
        var paragraph = box.addNewTextParagraph();
        for (int index = 0; index < line.words().size(); index++) {
            PdfPageContent.TextWord word = line.words().get(index);
            var run = paragraph.addNewTextRun();
            run.setText((index == 0 ? "" : " ") + word.text());
            run.setFontFamily("Arial");
            run.setFontSize(Math.max(
                6d,
                Math.min(
                    72d,
                    word.fontSize() * userUnit * transform.scale()
                )
            ));
            run.setBold(word.bold());
        }
    }

    private void writeTable(
            XSLFSlide slide,
            PdfPageContent page,
            SlideTransform transform,
            int lineIndex,
            TableCandidate candidate) {
        int rows = candidate.rows().size();
        int columns = candidate.rows().getFirst().size();
        List<AlignedTableDetector.Cell> first =
            candidate.rows().getFirst();
        double sourceLeft = first.getFirst().left() * page.userUnit();
        double[] sourceWidths = new double[columns];
        double sourceTableWidth = 0;
        for (int columnIndex = 0;
                columnIndex < columns;
                columnIndex++) {
            int column = columnIndex;
            double start = first.get(columnIndex).left();
            double positionedWidth = columnIndex + 1 < columns
                ? first.get(columnIndex + 1).left() - start
                : candidate.rows().stream()
                    .mapToDouble(row -> row.get(column).right() - start)
                    .max()
                    .orElse(1);
            double textWidth = candidate.rows().stream()
                .mapToDouble(row -> row.get(column).right()
                    - row.get(column).left())
                .max()
                .orElse(1);
            sourceWidths[columnIndex] = Math.max(
                positionedWidth,
                textWidth + 16
            ) * page.userUnit();
            sourceTableWidth += sourceWidths[columnIndex];
        }
        double sourceTop = page.lines().get(lineIndex).top()
            * page.userUnit();
        double sourceHeight = Math.max(
            rows * page.lines().get(lineIndex).fontSize()
                * page.userUnit() * 1.6,
            rows * 12
        );
        XSLFTable table = slide.createTable();
        for (int rowIndex = 0; rowIndex < rows; rowIndex++) {
            XSLFTableRow row = table.addRow();
            row.setHeight(sourceHeight * transform.scale() / rows);
            for (int columnIndex = 0;
                    columnIndex < columns;
                    columnIndex++) {
                XSLFTableCell cell = row.addCell();
                cell.setText(
                    candidate.rows().get(rowIndex)
                        .get(columnIndex).text()
                );
                cell.setLeftInset(2);
                cell.setRightInset(2);
                cell.setTopInset(1);
                cell.setBottomInset(1);
                cell.setFillColor(rowIndex == 0
                    ? new Color(235, 234, 250)
                    : Color.WHITE);
                for (var edge :
                        org.apache.poi.sl.usermodel.TableCell.BorderEdge
                            .values()) {
                    cell.setBorderColor(edge, new Color(190, 190, 205));
                    cell.setBorderWidth(edge, 0.5);
                }
                var textRun = cell.getTextParagraphs().getFirst()
                    .getTextRuns().getFirst();
                textRun.setFontFamily("Arial");
                textRun.setFontSize(Math.max(
                    6d,
                    page.lines().get(lineIndex + rowIndex).fontSize()
                        * page.userUnit()
                        * transform.scale()
                ));
                textRun.setBold(rowIndex == 0);
            }
        }
        for (int columnIndex = 0;
                columnIndex < columns;
                columnIndex++) {
            table.setColumnWidth(
                columnIndex,
                Math.max(
                    sourceWidths[columnIndex] * transform.scale(),
                    1
                )
            );
        }
        table.setAnchor(transform.rectangle(
            sourceLeft,
            sourceTop,
            sourceTableWidth,
            sourceHeight
        ));
    }

    private void writeImages(
            XMLSlideShow presentation,
            XSLFSlide slide,
            PdfPageContent page,
            SlideTransform transform) {
        for (PdfPageContent.PageImage image : page.images()) {
            XSLFPictureData data = presentation.addPicture(
                image.png(),
                PictureData.PictureType.PNG
            );
            XSLFPictureShape shape = slide.createPicture(data);
            shape.setAnchor(transform.rectangle(
                image.left() * page.userUnit(),
                image.top() * page.userUnit(),
                image.width() * page.userUnit(),
                image.height() * page.userUnit()
            ));
        }
    }

    private Dimension slideSize(
            PdfPageContent firstPage,
            String requested) {
        if (requested.equals("widescreen")) {
            return new Dimension(960, 540);
        }
        if (requested.equals("standard")) {
            return new Dimension(720, 540);
        }
        double width = firstPage.width() * firstPage.userUnit();
        double height = firstPage.height() * firstPage.userUnit();
        double scale = Math.min(
            1,
            MAX_SLIDE_POINTS / Math.max(width, height)
        );
        return new Dimension(
            Math.max((int) Math.round(width * scale), 1),
            Math.max((int) Math.round(height * scale), 1)
        );
    }

    private void writePresentation(
            XMLSlideShow presentation,
            Path output) throws IOException {
        try (OutputStream fileOutput = Files.newOutputStream(output);
             BoundedOutputStream bounded = new BoundedOutputStream(
                 fileOutput,
                 properties.getMaxOutputBytes(),
                 () -> {
                 }
             )) {
            presentation.write(bounded);
        } catch (OutputLimitExceededException exception) {
            throw new OperationException(
                "PDF_POWERPOINT_OUTPUT_LIMIT_EXCEEDED",
                "The PowerPoint presentation exceeds the output limit",
                exception
            );
        }
    }

    private record SlideTransform(
        double scale,
        double offsetX,
        double offsetY,
        double pageWidth,
        double pageHeight,
        Dimension slideSize
    ) {
        static SlideTransform create(
                PdfPageContent page,
                Dimension slideSize) {
            double width = page.width() * page.userUnit();
            double height = page.height() * page.userUnit();
            double scale = Math.min(
                slideSize.getWidth() / width,
                slideSize.getHeight() / height
            );
            return new SlideTransform(
                scale,
                (slideSize.getWidth() - width * scale) / 2,
                (slideSize.getHeight() - height * scale) / 2,
                width,
                height,
                slideSize
            );
        }

        Rectangle2D rectangle(
                double left,
                double top,
                double width,
                double height) {
            double x = Math.max(left, 0);
            double y = Math.max(top, 0);
            double clippedWidth = Math.max(
                Math.min(width, pageWidth - x),
                1
            );
            double clippedHeight = Math.max(
                Math.min(height, pageHeight - y),
                1
            );
            return new Rectangle2D.Double(
                offsetX + x * scale,
                offsetY + y * scale,
                clippedWidth * scale,
                clippedHeight * scale
            );
        }
    }
}
