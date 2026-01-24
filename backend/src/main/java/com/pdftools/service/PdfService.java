package com.pdftools.service;

import com.pdftools.dto.PdfOperationResult;
import com.pdftools.exception.PdfProcessingException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.util.Matrix;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.awt.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class PdfService {

    @Value("${pdf.upload.dir}")
    private String uploadDir;

    public PdfService() {
        // Constructor
    }

    /**
     * Merge multiple PDFs into one
     */
    public PdfOperationResult mergePdfs(List<MultipartFile> files) throws PdfProcessingException {
        try {
            PDDocument mergedDoc = new PDDocument();
            
            for (MultipartFile file : files) {
                try (PDDocument doc = Loader.loadPDF(file.getBytes())) {
                    for (PDPage page : doc.getPages()) {
                        mergedDoc.addPage(page);
                    }
                }
            }

            File outputFile = saveDocument(mergedDoc, "merged");
            mergedDoc.close();

            return new PdfOperationResult(true, "PDFs merged successfully", outputFile.getName());
        } catch (Exception e) {
            throw new PdfProcessingException("Failed to merge PDFs: " + e.getMessage(), e);
        }
    }

    /**
     * Split PDF into separate pages
     */
    public PdfOperationResult splitPdf(MultipartFile file) throws PdfProcessingException {
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            List<String> outputFiles = new ArrayList<>();
            int pageCount = document.getNumberOfPages();

            for (int i = 0; i < pageCount; i++) {
                PDDocument singlePageDoc = new PDDocument();
                singlePageDoc.addPage(document.getPage(i));
                
                File outputFile = saveDocument(singlePageDoc, "page_" + (i + 1));
                outputFiles.add(outputFile.getName());
                singlePageDoc.close();
            }

            return new PdfOperationResult(true, "PDF split into " + pageCount + " pages", 
                String.join(",", outputFiles));
        } catch (Exception e) {
            throw new PdfProcessingException("Failed to split PDF: " + e.getMessage(), e);
        }
    }

    /**
     * Extract specific pages from PDF
     */
    public PdfOperationResult extractPages(MultipartFile file, List<Integer> pageNumbers) 
            throws PdfProcessingException {
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            PDDocument extractedDoc = new PDDocument();

            for (Integer pageNum : pageNumbers) {
                if (pageNum > 0 && pageNum <= document.getNumberOfPages()) {
                    extractedDoc.addPage(document.getPage(pageNum - 1));
                }
            }

            File outputFile = saveDocument(extractedDoc, "extracted");
            extractedDoc.close();

            return new PdfOperationResult(true, "Pages extracted successfully", outputFile.getName());
        } catch (Exception e) {
            throw new PdfProcessingException("Failed to extract pages: " + e.getMessage(), e);
        }
    }

    /**
     * Remove specific pages from PDF
     */
    public PdfOperationResult removePages(MultipartFile file, List<Integer> pageNumbers) 
            throws PdfProcessingException {
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            // Sort in reverse order to remove from end to start
            pageNumbers.sort((a, b) -> b - a);
            
            for (Integer pageNum : pageNumbers) {
                if (pageNum > 0 && pageNum <= document.getNumberOfPages()) {
                    document.removePage(pageNum - 1);
                }
            }

            File outputFile = saveDocument(document, "removed_pages");

            return new PdfOperationResult(true, "Pages removed successfully", outputFile.getName());
        } catch (Exception e) {
            throw new PdfProcessingException("Failed to remove pages: " + e.getMessage(), e);
        }
    }

    /**
     * Add watermark to PDF
     */
    public PdfOperationResult addWatermark(MultipartFile file, String watermarkText) 
            throws PdfProcessingException {
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            for (PDPage page : document.getPages()) {
                PDPageContentStream contentStream = new PDPageContentStream(
                    document, page, PDPageContentStream.AppendMode.APPEND, true, true);

                // Set watermark properties
                contentStream.setNonStrokingColor(Color.LIGHT_GRAY);
                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 60);
                
                // Center and rotate watermark
                PDRectangle pageSize = page.getMediaBox();
                float pageWidth = pageSize.getWidth();
                float pageHeight = pageSize.getHeight();
                
                contentStream.setTextMatrix(Matrix.getRotateInstance(Math.toRadians(45), 
                    pageWidth / 2, pageHeight / 2));
                contentStream.showText(watermarkText);
                contentStream.endText();
                contentStream.close();
            }

            File outputFile = saveDocument(document, "watermarked");

            return new PdfOperationResult(true, "Watermark added successfully", outputFile.getName());
        } catch (Exception e) {
            throw new PdfProcessingException("Failed to add watermark: " + e.getMessage(), e);
        }
    }

    /**
     * Add text to PDF
     */
    public PdfOperationResult addText(MultipartFile file, String text, float x, float y, int pageNum) 
            throws PdfProcessingException {
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            if (pageNum < 1 || pageNum > document.getNumberOfPages()) {
                throw new PdfProcessingException("Invalid page number");
            }

            PDPage page = document.getPage(pageNum - 1);
            PDPageContentStream contentStream = new PDPageContentStream(
                document, page, PDPageContentStream.AppendMode.APPEND, true, true);

            contentStream.beginText();
            contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
            contentStream.newLineAtOffset(x, y);
            contentStream.showText(text);
            contentStream.endText();
            contentStream.close();

            File outputFile = saveDocument(document, "text_added");

            return new PdfOperationResult(true, "Text added successfully", outputFile.getName());
        } catch (Exception e) {
            throw new PdfProcessingException("Failed to add text: " + e.getMessage(), e);
        }
    }

    /**
     * Add signature image to PDF
     */
    public PdfOperationResult addSignature(MultipartFile pdfFile, MultipartFile signatureFile, 
            float x, float y, int pageNum) throws PdfProcessingException {
        try (PDDocument document = Loader.loadPDF(pdfFile.getBytes())) {
            if (pageNum < 1 || pageNum > document.getNumberOfPages()) {
                throw new PdfProcessingException("Invalid page number");
            }

            PDPage page = document.getPage(pageNum - 1);
            PDImageXObject pdImage = PDImageXObject.createFromByteArray(
                document, signatureFile.getBytes(), signatureFile.getOriginalFilename());

            PDPageContentStream contentStream = new PDPageContentStream(
                document, page, PDPageContentStream.AppendMode.APPEND, true, true);

            // Draw signature with appropriate size
            float scale = 0.3f;
            contentStream.drawImage(pdImage, x, y, 
                pdImage.getWidth() * scale, pdImage.getHeight() * scale);
            contentStream.close();

            File outputFile = saveDocument(document, "signed");

            return new PdfOperationResult(true, "Signature added successfully", outputFile.getName());
        } catch (Exception e) {
            throw new PdfProcessingException("Failed to add signature: " + e.getMessage(), e);
        }
    }

    /**
     * Redact text in PDF (simple black box redaction)
     */
    public PdfOperationResult redactText(MultipartFile file, float x, float y, float width, 
            float height, int pageNum) throws PdfProcessingException {
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            if (pageNum < 1 || pageNum > document.getNumberOfPages()) {
                throw new PdfProcessingException("Invalid page number");
            }

            PDPage page = document.getPage(pageNum - 1);
            PDPageContentStream contentStream = new PDPageContentStream(
                document, page, PDPageContentStream.AppendMode.APPEND, true, true);

            // Draw black rectangle for redaction
            contentStream.setNonStrokingColor(Color.BLACK);
            contentStream.addRect(x, y, width, height);
            contentStream.fill();
            contentStream.close();

            File outputFile = saveDocument(document, "redacted");

            return new PdfOperationResult(true, "Content redacted successfully", outputFile.getName());
        } catch (Exception e) {
            throw new PdfProcessingException("Failed to redact content: " + e.getMessage(), e);
        }
    }

    /**
     * Convert PDF to Markdown
     */
    public PdfOperationResult convertToMarkdown(MultipartFile file) throws PdfProcessingException {
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);

            // Simple markdown conversion
            StringBuilder markdown = new StringBuilder();
            markdown.append("# PDF Content\n\n");
            
            String[] lines = text.split("\n");
            for (String line : lines) {
                if (line.trim().isEmpty()) {
                    markdown.append("\n");
                } else {
                    markdown.append(line).append("\n\n");
                }
            }

            File outputFile = new File(getUploadDir(), 
                UUID.randomUUID().toString() + ".md");
            Files.write(outputFile.toPath(), markdown.toString().getBytes());

            return new PdfOperationResult(true, "PDF converted to Markdown", outputFile.getName());
        } catch (Exception e) {
            throw new PdfProcessingException("Failed to convert to Markdown: " + e.getMessage(), e);
        }
    }

    /**
     * Convert PDF to DOCX
     */
    public PdfOperationResult convertToDocx(MultipartFile file) throws PdfProcessingException {
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);

            XWPFDocument docxDocument = new XWPFDocument();
            
            String[] paragraphs = text.split("\n\n");
            for (String para : paragraphs) {
                if (!para.trim().isEmpty()) {
                    XWPFParagraph paragraph = docxDocument.createParagraph();
                    XWPFRun run = paragraph.createRun();
                    run.setText(para.trim());
                }
            }

            File outputFile = new File(getUploadDir(), 
                UUID.randomUUID().toString() + ".docx");
            try (FileOutputStream out = new FileOutputStream(outputFile)) {
                docxDocument.write(out);
            }
            docxDocument.close();

            return new PdfOperationResult(true, "PDF converted to DOCX", outputFile.getName());
        } catch (Exception e) {
            throw new PdfProcessingException("Failed to convert to DOCX: " + e.getMessage(), e);
        }
    }

    /**
     * Get PDF information
     */
    public PdfOperationResult getPdfInfo(MultipartFile file) throws PdfProcessingException {
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            int pageCount = document.getNumberOfPages();
            String info = String.format("Pages: %d", pageCount);

            return new PdfOperationResult(true, info, null);
        } catch (Exception e) {
            throw new PdfProcessingException("Failed to get PDF info: " + e.getMessage(), e);
        }
    }

    /**
     * Helper method to save document
     */
    private File saveDocument(PDDocument document, String prefix) throws IOException {
        File outputFile = new File(getUploadDir(), 
            prefix + "_" + UUID.randomUUID().toString() + ".pdf");
        document.save(outputFile);
        return outputFile;
    }

    /**
     * Get upload directory, create if it doesn't exist
     */
    private File getUploadDir() throws IOException {
        File dir = new File(uploadDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    /**
     * Download file
     */
    public byte[] downloadFile(String filename) throws PdfProcessingException {
        try {
            Path filePath = Paths.get(uploadDir, filename);
            return Files.readAllBytes(filePath);
        } catch (Exception e) {
            throw new PdfProcessingException("Failed to download file: " + e.getMessage(), e);
        }
    }
}
