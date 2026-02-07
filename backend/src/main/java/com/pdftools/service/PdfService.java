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
    public PdfOperationResult mergePdfs(List<MultipartFile> files, String originalFilename) throws PdfProcessingException {
        List<PDDocument> sourceDocs = new ArrayList<>();
        try {
            PDDocument mergedDoc = new PDDocument();
            
            for (MultipartFile file : files) {
                PDDocument doc = Loader.loadPDF(file.getBytes());
                sourceDocs.add(doc); // Keep reference to prevent closing
                
                for (int i = 0; i < doc.getNumberOfPages(); i++) {
                    PDPage page = doc.getPage(i);
                    // Import page to new document to avoid reference issues
                    mergedDoc.importPage(page);
                }
            }

            File outputFile = saveDocument(mergedDoc, "merged", originalFilename);
            mergedDoc.close();
            
            // Close source documents after merged doc is saved
            for (PDDocument doc : sourceDocs) {
                doc.close();
            }

            return new PdfOperationResult(true, "PDFs merged successfully", outputFile.getName());
        } catch (Exception e) {
            // Clean up on error
            for (PDDocument doc : sourceDocs) {
                try { doc.close(); } catch (Exception ignored) {}
            }
            throw new PdfProcessingException("Failed to merge PDFs: " + e.getMessage(), e);
        }
    }

    /**
     * Split PDF into separate documents based on page groups
     * @param groups - comma-separated page groups, e.g. "1-3,4-5" creates two PDFs
     *                 If null or empty, splits into individual pages
     */
    public PdfOperationResult splitPdf(MultipartFile file, String groups, String originalFilename) throws PdfProcessingException {
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            List<String> outputFiles = new ArrayList<>();
            int pageCount = document.getNumberOfPages();
            String baseName = getBaseFilename(originalFilename, "split");

            if (groups == null || groups.trim().isEmpty()) {
                // Split into individual pages (legacy behavior)
                for (int i = 0; i < pageCount; i++) {
                    PDDocument singlePageDoc = new PDDocument();
                    singlePageDoc.addPage(document.getPage(i));
                    
                    File outputFile = new File(getUploadDir(),
                        baseName + "_page" + (i + 1) + "_" + UUID.randomUUID().toString().substring(0, 8) + ".pdf");
                    singlePageDoc.save(outputFile);
                    outputFiles.add(outputFile.getName());
                    singlePageDoc.close();
                }
            } else {
                // Split into custom groups
                String[] groupArray = groups.split(";");
                int groupNum = 1;
                for (String group : groupArray) {
                    PDDocument groupDoc = new PDDocument();
                    List<Integer> pageNums = parsePageGroup(group.trim(), pageCount);
                    
                    for (Integer pageNum : pageNums) {
                        if (pageNum > 0 && pageNum <= pageCount) {
                            groupDoc.addPage(document.getPage(pageNum - 1));
                        }
                    }
                    
                    if (groupDoc.getNumberOfPages() > 0) {
                        File outputFile = new File(getUploadDir(),
                            baseName + "_part" + groupNum + "_" + UUID.randomUUID().toString().substring(0, 8) + ".pdf");
                        groupDoc.save(outputFile);
                        outputFiles.add(outputFile.getName());
                    }
                    groupDoc.close();
                    groupNum++;
                }
            }

            return new PdfOperationResult(true, "PDF split into " + outputFiles.size() + " documents", 
                String.join(",", outputFiles));
        } catch (Exception e) {
            throw new PdfProcessingException("Failed to split PDF: " + e.getMessage(), e);
        }
    }

    /**
     * Parse page group string like "1-3" or "1,2,5" or "1-3,5"
     */
    private List<Integer> parsePageGroup(String group, int maxPages) {
        List<Integer> pages = new ArrayList<>();
        String[] parts = group.split(",");
        for (String part : parts) {
            part = part.trim();
            if (part.contains("-")) {
                String[] range = part.split("-");
                int start = Integer.parseInt(range[0].trim());
                int end = Integer.parseInt(range[1].trim());
                for (int i = start; i <= end && i <= maxPages; i++) {
                    if (i > 0 && !pages.contains(i)) {
                        pages.add(i);
                    }
                }
            } else {
                int pageNum = Integer.parseInt(part);
                if (pageNum > 0 && pageNum <= maxPages && !pages.contains(pageNum)) {
                    pages.add(pageNum);
                }
            }
        }
        return pages;
    }

    /**
     * Extract specific pages from PDF
     */
    public PdfOperationResult extractPages(MultipartFile file, List<Integer> pageNumbers, String originalFilename) 
            throws PdfProcessingException {
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            PDDocument extractedDoc = new PDDocument();

            for (Integer pageNum : pageNumbers) {
                if (pageNum > 0 && pageNum <= document.getNumberOfPages()) {
                    extractedDoc.addPage(document.getPage(pageNum - 1));
                }
            }

            File outputFile = saveDocument(extractedDoc, "extracted", originalFilename);
            extractedDoc.close();

            return new PdfOperationResult(true, "Pages extracted successfully", outputFile.getName());
        } catch (Exception e) {
            throw new PdfProcessingException("Failed to extract pages: " + e.getMessage(), e);
        }
    }

    /**
     * Remove specific pages from PDF
     */
    public PdfOperationResult removePages(MultipartFile file, List<Integer> pageNumbers, String originalFilename) 
            throws PdfProcessingException {
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            // Sort in reverse order to remove from end to start
            pageNumbers.sort((a, b) -> b - a);
            
            for (Integer pageNum : pageNumbers) {
                if (pageNum > 0 && pageNum <= document.getNumberOfPages()) {
                    document.removePage(pageNum - 1);
                }
            }

            File outputFile = saveDocument(document, "removed", originalFilename);

            return new PdfOperationResult(true, "Pages removed successfully", outputFile.getName());
        } catch (Exception e) {
            throw new PdfProcessingException("Failed to remove pages: " + e.getMessage(), e);
        }
    }

    /**
     * Add watermark to PDF with positioning
     */
    public PdfOperationResult addWatermark(MultipartFile file, String watermarkText, 
            Float x, Float y, float rotation, float opacity, String originalFilename) 
            throws PdfProcessingException {
        // Enforce max 30 chars
        if (watermarkText.length() > 30) {
            watermarkText = watermarkText.substring(0, 30);
        }
        
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            for (PDPage page : document.getPages()) {
                PDPageContentStream contentStream = new PDPageContentStream(
                    document, page, PDPageContentStream.AppendMode.APPEND, true, true);

                // Set watermark properties with opacity
                int grayValue = (int)(255 * (1 - opacity));
                contentStream.setNonStrokingColor(new Color(grayValue, grayValue, grayValue));
                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 60);
                
                PDRectangle pageSize = page.getMediaBox();
                float pageWidth = pageSize.getWidth();
                float pageHeight = pageSize.getHeight();
                
                // Use provided position or center
                float posX = (x != null) ? x : pageWidth / 2;
                float posY = (y != null) ? y : pageHeight / 2;
                
                contentStream.setTextMatrix(Matrix.getRotateInstance(Math.toRadians(rotation), posX, posY));
                contentStream.showText(watermarkText);
                contentStream.endText();
                contentStream.close();
            }

            File outputFile = saveDocument(document, "watermarked", originalFilename);

            return new PdfOperationResult(true, "Watermark added successfully", outputFile.getName());
        } catch (Exception e) {
            throw new PdfProcessingException("Failed to add watermark: " + e.getMessage(), e);
        }
    }

    /**
     * Add text to PDF with font customization
     */
    public PdfOperationResult addText(MultipartFile file, String text, float x, float y, int pageNum, 
            float fontSize, String fontName, String fontColor, String originalFilename) 
            throws PdfProcessingException {
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            if (pageNum < 1 || pageNum > document.getNumberOfPages()) {
                throw new PdfProcessingException("Invalid page number");
            }

            PDPage page = document.getPage(pageNum - 1);
            PDPageContentStream contentStream = new PDPageContentStream(
                document, page, PDPageContentStream.AppendMode.APPEND, true, true);

            // Parse font name
            Standard14Fonts.FontName font = Standard14Fonts.FontName.HELVETICA;
            try {
                font = Standard14Fonts.FontName.valueOf(fontName.toUpperCase().replace("-", "_"));
            } catch (IllegalArgumentException ignored) {
                // Use default HELVETICA if invalid font name
            }

            // Parse color from hex
            Color color = Color.BLACK;
            try {
                color = Color.decode(fontColor);
            } catch (NumberFormatException ignored) {
                // Use black if invalid color
            }

            contentStream.beginText();
            contentStream.setFont(new PDType1Font(font), fontSize);
            contentStream.setNonStrokingColor(color);
            contentStream.newLineAtOffset(x, y);
            contentStream.showText(text);
            contentStream.endText();
            contentStream.close();

            File outputFile = saveDocument(document, "text_added", originalFilename);

            return new PdfOperationResult(true, "Text added successfully", outputFile.getName());
        } catch (Exception e) {
            throw new PdfProcessingException("Failed to add text: " + e.getMessage(), e);
        }
    }

    /**
     * Add signature image to PDF
     */
    public PdfOperationResult addSignature(MultipartFile pdfFile, MultipartFile signatureFile, 
            float x, float y, int pageNum, String originalFilename) throws PdfProcessingException {
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

            File outputFile = saveDocument(document, "signed", originalFilename);

            return new PdfOperationResult(true, "Signature added successfully", outputFile.getName());
        } catch (Exception e) {
            throw new PdfProcessingException("Failed to add signature: " + e.getMessage(), e);
        }
    }

    /**
     * Redact text in PDF (simple black box redaction)
     */
    public PdfOperationResult redactText(MultipartFile file, float x, float y, float width, 
            float height, int pageNum, String originalFilename) throws PdfProcessingException {
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

            File outputFile = saveDocument(document, "redacted", originalFilename);

            return new PdfOperationResult(true, "Content redacted successfully", outputFile.getName());
        } catch (Exception e) {
            throw new PdfProcessingException("Failed to redact content: " + e.getMessage(), e);
        }
    }

    /**
     * Redact multiple areas in PDF
     */
    public PdfOperationResult redactMultiple(MultipartFile file, String redactionsJson, String originalFilename) 
            throws PdfProcessingException {
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            // Parse JSON array of redactions: [{x, y, width, height, pageNum}, ...]
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            java.util.List<java.util.Map<String, Object>> redactions = mapper.readValue(redactionsJson, 
                new com.fasterxml.jackson.core.type.TypeReference<java.util.List<java.util.Map<String, Object>>>(){});
            
            for (java.util.Map<String, Object> redaction : redactions) {
                int pageNum = ((Number) redaction.get("pageNum")).intValue();
                float x = ((Number) redaction.get("x")).floatValue();
                float y = ((Number) redaction.get("y")).floatValue();
                float width = ((Number) redaction.get("width")).floatValue();
                float height = ((Number) redaction.get("height")).floatValue();
                
                if (pageNum < 1 || pageNum > document.getNumberOfPages()) continue;
                
                PDPage page = document.getPage(pageNum - 1);
                PDPageContentStream contentStream = new PDPageContentStream(
                    document, page, PDPageContentStream.AppendMode.APPEND, true, true);
                
                contentStream.setNonStrokingColor(Color.BLACK);
                contentStream.addRect(x, y, width, height);
                contentStream.fill();
                contentStream.close();
            }

            File outputFile = saveDocument(document, "redacted", originalFilename);

            return new PdfOperationResult(true, "Content redacted successfully", outputFile.getName());
        } catch (Exception e) {
            throw new PdfProcessingException("Failed to redact content: " + e.getMessage(), e);
        }
    }

    /**
     * Convert PDF to Markdown
     */
    public PdfOperationResult convertToMarkdown(MultipartFile file, String originalFilename) throws PdfProcessingException {
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = stripper.getText(document);

            // Check if any text was extracted
            if (text == null || text.trim().isEmpty()) {
                // Return a message indicating no text could be extracted
                text = "[No extractable text found in this PDF. The document may contain only images or scanned content.]";
            }

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

            String baseName = getBaseFilename(originalFilename, "markdown");
            File outputFile = new File(getUploadDir(), 
                baseName + "_" + UUID.randomUUID().toString().substring(0, 8) + ".md");
            Files.write(outputFile.toPath(), markdown.toString().getBytes());

            return new PdfOperationResult(true, "PDF converted to Markdown", outputFile.getName());
        } catch (Exception e) {
            throw new PdfProcessingException("Failed to convert to Markdown: " + e.getMessage(), e);
        }
    }

    /**
     * Convert PDF to DOCX
     */
    public PdfOperationResult convertToDocx(MultipartFile file, String originalFilename) throws PdfProcessingException {
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = stripper.getText(document);

            XWPFDocument docxDocument = new XWPFDocument();
            
            // Check if any text was extracted
            if (text == null || text.trim().isEmpty()) {
                // Add a paragraph indicating no text was found
                XWPFParagraph paragraph = docxDocument.createParagraph();
                XWPFRun run = paragraph.createRun();
                run.setText("[No extractable text found in this PDF. The document may contain only images or scanned content.]");
            } else {
                String[] paragraphs = text.split("\n\n");
                for (String para : paragraphs) {
                    if (!para.trim().isEmpty()) {
                        XWPFParagraph paragraph = docxDocument.createParagraph();
                        XWPFRun run = paragraph.createRun();
                        run.setText(para.trim());
                    }
                }
            }

            String baseName = getBaseFilename(originalFilename, "docx");
            File outputFile = new File(getUploadDir(), 
                baseName + "_" + UUID.randomUUID().toString().substring(0, 8) + ".docx");
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
     * Helper method to save document with original filename and operation suffix
     */
    private File saveDocument(PDDocument document, String operationSuffix, String originalFilename) throws IOException {
        String baseName = getBaseFilename(originalFilename, operationSuffix);
        File outputFile = new File(getUploadDir(), 
            baseName + "_" + UUID.randomUUID().toString().substring(0, 8) + ".pdf");
        document.save(outputFile);
        return outputFile;
    }

    /**
     * Helper method to save document (legacy, without original filename)
     */
    private File saveDocument(PDDocument document, String prefix) throws IOException {
        return saveDocument(document, prefix, null);
    }

    /**
     * Get base filename from original filename or use default prefix
     */
    private String getBaseFilename(String originalFilename, String operationSuffix) {
        if (originalFilename != null && !originalFilename.isEmpty()) {
            // Remove .pdf extension and add operation suffix
            String baseName = originalFilename.replaceAll("\\.[pP][dD][fF]$", "");
            return baseName + "_" + operationSuffix;
        }
        return operationSuffix;
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
     * Validate filename to prevent path traversal attacks
     * @param filename The filename to validate
     * @throws PdfProcessingException if the filename is invalid or contains path traversal attempts
     */
    private void validateFilename(String filename) throws PdfProcessingException {
        if (filename == null || filename.trim().isEmpty()) {
            throw new PdfProcessingException("Filename cannot be null or empty");
        }
        
        // Reject filenames with null bytes (common security issue)
        if (filename.contains("\0")) {
            throw new PdfProcessingException("Invalid filename: null byte detected");
        }
        
        // Only allow alphanumeric characters, single dots, hyphens, underscores
        // This regex prevents path separators, parent directory references, multiple consecutive dots,
        // and enforces valid file extensions (.pdf, .md, .docx)
        if (!filename.matches("^[a-zA-Z0-9]([a-zA-Z0-9._-]*[a-zA-Z0-9])?\\.(pdf|md|docx)$")) {
            throw new PdfProcessingException("Invalid filename: only alphanumeric characters, dots, hyphens, and underscores are allowed with .pdf, .md, or .docx extensions");
        }
    }
    
    /**
     * Download file
     */
    public byte[] downloadFile(String filename) throws PdfProcessingException {
        try {
            // Validate filename to prevent path traversal attacks
            validateFilename(filename);
            
            Path filePath = Paths.get(uploadDir, filename);
            
            // Additional security check: ensure the resolved path is within the upload directory
            Path uploadPath = Paths.get(uploadDir).toRealPath();
            Path resolvedPath = filePath.toRealPath();
            
            if (!resolvedPath.startsWith(uploadPath)) {
                throw new PdfProcessingException("Access denied: file is outside the allowed directory");
            }
            
            return Files.readAllBytes(resolvedPath);
        } catch (PdfProcessingException e) {
            throw e;
        } catch (java.nio.file.NoSuchFileException e) {
            throw new PdfProcessingException("File not found: " + filename, e);
        } catch (Exception e) {
            throw new PdfProcessingException("Failed to download file: " + e.getMessage(), e);
        }
    }
}
