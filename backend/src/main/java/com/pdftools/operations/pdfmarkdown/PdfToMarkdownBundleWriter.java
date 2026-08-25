package com.pdftools.operations.pdfmarkdown;

import com.pdftools.operations.BoundedOutputStream;
import com.pdftools.operations.OperationException;
import com.pdftools.operations.OutputLimitExceededException;
import com.pdftools.operations.shared.extraction.AlignedTableDetector;
import com.pdftools.operations.shared.extraction.AlignedTableDetector.TableCandidate;
import com.pdftools.operations.shared.extraction.PdfPageContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

final class PdfToMarkdownBundleWriter {

    private static final Logger logger =
        LoggerFactory.getLogger(PdfToMarkdownBundleWriter.class);
    private static final Pattern BULLET = Pattern.compile(
        "^(?:[-*]|[\\u2022\\u25CF\\u25AA])\\s+(.+)$"
    );
    private static final Pattern NUMBERED = Pattern.compile(
        "^\\d+[.)]\\s+(.+)$"
    );

    private final PdfToMarkdownProperties properties;
    private final AlignedTableDetector tableDetector;
    private final StringBuilder markdown = new StringBuilder();
    private int tables;

    PdfToMarkdownBundleWriter(PdfToMarkdownProperties properties) {
        this.properties = properties;
        this.tableDetector = new AlignedTableDetector(
            properties.getMaxTableColumns()
        );
    }

    void write(
            List<PdfPageContent> pages,
            PdfToMarkdownPlanFactory.PdfToMarkdownPlan plan,
            Path output,
            Runnable progress) {
        for (int pageIndex = 0;
                pageIndex < pages.size();
                pageIndex++) {
            if (plan.preservePageBreaks()) {
                if (pageIndex > 0) {
                    append("\n\n---\n\n");
                }
                append("<!-- Page " + (pageIndex + 1) + " -->\n\n");
            }
            writePage(pages.get(pageIndex), pageIndex, plan);
            progress.run();
        }
        writeZip(pages, plan, output);
    }

    private void writePage(
            PdfPageContent page,
            int pageIndex,
            PdfToMarkdownPlanFactory.PdfToMarkdownPlan plan) {
        float medianFont = medianFont(page.lines());
        List<PdfPageContent.PageImage> images = plan.includeImages()
            ? page.images()
            : List.of();
        int imageIndex = 0;
        int lineIndex = 0;
        while (lineIndex < page.lines().size()) {
            while (imageIndex < images.size()
                    && images.get(imageIndex).top()
                        <= page.lines().get(lineIndex).top()) {
                writeImage(pageIndex, imageIndex);
                imageIndex++;
            }
            TableCandidate table = plan.detectTables()
                ? tableDetector.tableAt(page.lines(), lineIndex)
                : null;
            if (table != null) {
                writeTable(table);
                lineIndex += table.rows().size();
            } else {
                writeLine(
                    page.lines().get(lineIndex),
                    medianFont,
                    plan
                );
                lineIndex++;
            }
        }
        while (imageIndex < images.size()) {
            writeImage(pageIndex, imageIndex);
            imageIndex++;
        }
    }

    private void writeLine(
            PdfPageContent.TextLine line,
            float medianFont,
            PdfToMarkdownPlanFactory.PdfToMarkdownPlan plan) {
        String text = line.text().strip();
        if (text.isEmpty()) {
            return;
        }
        if (plan.detectLists()) {
            Matcher bullet = BULLET.matcher(text);
            if (bullet.matches()) {
                append("- " + escape(bullet.group(1)) + "\n");
                return;
            }
            Matcher numbered = NUMBERED.matcher(text);
            if (numbered.matches()) {
                append("1. " + escape(numbered.group(1)) + "\n");
                return;
            }
        }
        if (plan.detectHeadings()
                && text.length() <= 160
                && line.fontSize() >= medianFont * 1.55f) {
            append("# " + escape(text) + "\n\n");
        } else if (plan.detectHeadings()
                && text.length() <= 180
                && line.fontSize() >= medianFont * 1.3f) {
            append("## " + escape(text) + "\n\n");
        } else {
            append(escapePlainLine(text) + "\n\n");
        }
    }

    private void writeTable(TableCandidate table) {
        tables++;
        if (tables > properties.getMaxTables()) {
            throw new OperationException(
                "PDF_MARKDOWN_TABLE_LIMIT_EXCEEDED",
                "The PDF exceeds the Markdown table limit"
            );
        }
        writeTableRow(table.rows().getFirst());
        append("|");
        for (int column = 0;
                column < table.rows().getFirst().size();
                column++) {
            append(" --- |");
        }
        append("\n");
        for (int row = 1; row < table.rows().size(); row++) {
            writeTableRow(table.rows().get(row));
        }
        append("\n");
    }

    private void writeTableRow(
            List<AlignedTableDetector.Cell> row) {
        append("|");
        for (AlignedTableDetector.Cell cell : row) {
            append(" " + escapeTable(cell.text()) + " |");
        }
        append("\n");
    }

    private void writeImage(int pageIndex, int imageIndex) {
        String name = imageName(pageIndex, imageIndex);
        append(
            "![Page " + (pageIndex + 1)
                + " image " + (imageIndex + 1) + "]("
                + name + ")\n\n"
        );
    }

    private void writeZip(
            List<PdfPageContent> pages,
            PdfToMarkdownPlanFactory.PdfToMarkdownPlan plan,
            Path output) {
        try (OutputStream fileOutput = Files.newOutputStream(output);
             BoundedOutputStream bounded = new BoundedOutputStream(
                 fileOutput,
                 properties.getMaxOutputBytes(),
                 () -> {
                 }
             );
             ZipOutputStream zip = new ZipOutputStream(
                 new BufferedOutputStream(bounded)
             )) {
            zip.putNextEntry(entry("document.md"));
            zip.write(markdown.toString().getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            if (plan.includeImages()) {
                for (int pageIndex = 0;
                        pageIndex < pages.size();
                        pageIndex++) {
                    List<PdfPageContent.PageImage> images =
                        pages.get(pageIndex).images();
                    for (int imageIndex = 0;
                            imageIndex < images.size();
                            imageIndex++) {
                        zip.putNextEntry(entry(
                            imageName(pageIndex, imageIndex)
                        ));
                        zip.write(images.get(imageIndex).png());
                        zip.closeEntry();
                    }
                }
            }
        } catch (OutputLimitExceededException exception) {
            OperationException failure = new OperationException(
                "PDF_MARKDOWN_OUTPUT_LIMIT_EXCEEDED",
                "The Markdown bundle exceeds the output limit",
                exception
            );
            deletePartial(output, failure);
            throw failure;
        } catch (IOException exception) {
            OperationException failure = new OperationException(
                "PDF_MARKDOWN_WRITE_FAILED",
                "The Markdown bundle could not be generated",
                exception
            );
            deletePartial(output, failure);
            throw failure;
        }
    }

    private ZipEntry entry(String name) {
        ZipEntry entry = new ZipEntry(name);
        entry.setTime(0L);
        return entry;
    }

    private String imageName(int pageIndex, int imageIndex) {
        return "images/page-%03d-image-%03d.png".formatted(
            pageIndex + 1,
            imageIndex + 1
        );
    }

    private String escape(String text) {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\\", "\\\\")
            .replace("`", "\\`")
            .replace("*", "\\*")
            .replace("_", "\\_")
            .replace("#", "\\#")
            .replace("[", "\\[")
            .replace("]", "\\]")
            .replace("\r", " ")
            .replace("\n", " ");
    }

    private String escapePlainLine(String text) {
        return escape(text)
            .replaceFirst("^(\\s*)([-+])(\\s+)", "$1\\\\$2$3")
            .replaceFirst("^(\\s*\\d+)([.)])(\\s+)", "$1\\\\$2$3");
    }

    private String escapeTable(String text) {
        return escape(text)
            .replace("|", "\\|")
            .replace("\r", " ")
            .replace("\n", " ");
    }

    private float medianFont(List<PdfPageContent.TextLine> lines) {
        if (lines.isEmpty()) {
            return 11;
        }
        List<Float> sizes = lines.stream()
            .map(PdfPageContent.TextLine::fontSize)
            .sorted()
            .toList();
        return sizes.get(sizes.size() / 2);
    }

    private void append(String value) {
        if (markdown.length()
                > properties.getMaxMarkdownCharacters() - value.length()) {
            throw new OperationException(
                "PDF_MARKDOWN_TEXT_LIMIT_EXCEEDED",
                "The generated Markdown exceeds the text limit"
            );
        }
        markdown.append(value);
    }

    private void deletePartial(
            Path output,
            OperationException failure) {
        try {
            Files.deleteIfExists(output);
        } catch (IOException exception) {
            failure.addSuppressed(exception);
            logger.error(
                "Could not remove partial PDF-to-Markdown bundle {}",
                output,
                exception
            );
        }
    }
}
