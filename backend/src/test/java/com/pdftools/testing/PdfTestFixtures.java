package com.pdftools.testing;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class PdfTestFixtures {

    private PdfTestFixtures() {
    }

    public static Path coloredPdf(Path path, List<PageSpec> pages) throws IOException {
        try (PDDocument document = new PDDocument()) {
            for (PageSpec pageSpec : pages) {
                PDPage page = new PDPage(new PDRectangle(pageSpec.width(), pageSpec.height()));
                document.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    content.setNonStrokingColor(pageSpec.color());
                    content.addRect(0, 0, pageSpec.width(), pageSpec.height());
                    content.fill();
                }
            }
            Files.createDirectories(path.getParent());
            document.save(path.toFile());
        }
        return path;
    }

    public static byte[] coloredPdfBytes(List<PageSpec> pages) throws IOException {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (PageSpec pageSpec : pages) {
                PDPage page = new PDPage(new PDRectangle(pageSpec.width(), pageSpec.height()));
                document.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    content.setNonStrokingColor(pageSpec.color());
                    content.addRect(0, 0, pageSpec.width(), pageSpec.height());
                    content.fill();
                }
            }
            document.save(output);
            return output.toByteArray();
        }
    }

    public record PageSpec(float width, float height, Color color) {
    }
}
