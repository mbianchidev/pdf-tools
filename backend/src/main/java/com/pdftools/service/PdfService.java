package com.pdftools.service;

import com.pdftools.dto.PdfOperationResult;
import com.pdftools.exception.PdfProcessingException;
import com.pdftools.config.JobProperties;
import com.pdftools.operations.OperationException;
import com.pdftools.operations.OperationCancelledException;
import com.pdftools.operations.LegacyWorkspaceRegistry;
import com.pdftools.operations.LegacyOperationGuard;
import com.pdftools.operations.OperationOutput;
import com.pdftools.operations.ZipArtifactService;
import com.pdftools.operations.merge.MergeProperties;
import com.pdftools.operations.merge.MergeSource;
import com.pdftools.operations.merge.PdfMergeEngine;
import com.pdftools.operations.shared.pages.PageExpressionParser;
import com.pdftools.operations.split.PdfSplitEngine;
import com.pdftools.operations.split.SplitPlanFactory;
import com.pdftools.operations.split.SplitProperties;
import com.pdftools.operations.split.SplitResult;
import com.pdftools.util.FilenameSanitizer;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.awt.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.util.ArrayList;
import java.util.List;
import java.util.Comparator;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class PdfService {

    private static final Logger logger = LoggerFactory.getLogger(PdfService.class);
    private static final Set<String> PDF_MEDIA_TYPES = Set.of(
        "application/pdf",
        "application/x-pdf",
        "application/octet-stream"
    );
    private static final int MAX_LEGACY_SPLIT_GROUPS_LENGTH = 65_536;

    @Value("${pdf.upload.dir}")
    private String uploadDir;

    private final PdfMergeEngine mergeEngine;
    private final MergeProperties mergeProperties;
    private final JobProperties jobProperties;
    private final LegacyWorkspaceRegistry legacyWorkspaceRegistry;
    private final PdfSplitEngine splitEngine;
    private final SplitProperties splitProperties;
    private final ZipArtifactService zipArtifactService;
    private final ObjectMapper objectMapper;

    public PdfService() {
        this.mergeProperties = new MergeProperties();
        this.mergeEngine = new PdfMergeEngine(mergeProperties);
        this.jobProperties = new JobProperties();
        this.legacyWorkspaceRegistry = new LegacyWorkspaceRegistry();
        SplitProperties splitProperties = new SplitProperties();
        this.splitProperties = splitProperties;
        this.splitEngine = new PdfSplitEngine(new SplitPlanFactory(
            new PageExpressionParser(),
            splitProperties
        ), splitProperties);
        this.zipArtifactService = new ZipArtifactService();
        this.objectMapper = new ObjectMapper();
    }

    @Autowired
    public PdfService(
            PdfMergeEngine mergeEngine,
            MergeProperties mergeProperties,
            JobProperties jobProperties,
            LegacyWorkspaceRegistry legacyWorkspaceRegistry,
            PdfSplitEngine splitEngine,
            SplitProperties splitProperties,
            ZipArtifactService zipArtifactService,
            ObjectMapper objectMapper) {
        this.mergeEngine = mergeEngine;
        this.mergeProperties = mergeProperties;
        this.jobProperties = jobProperties;
        this.legacyWorkspaceRegistry = legacyWorkspaceRegistry;
        this.splitEngine = splitEngine;
        this.splitProperties = splitProperties;
        this.zipArtifactService = zipArtifactService;
        this.objectMapper = objectMapper;
    }

    /**
     * Merge multiple PDFs into one
     */
    public PdfOperationResult mergePdfs(List<MultipartFile> files, String originalFilename) throws PdfProcessingException {
        validateLegacyMergeFiles(files);
        Path workspace = null;
        Path outputPath = null;
        FileChannel workspaceLockChannel = null;
        FileLock workspaceLock = null;
        try {
            Files.createDirectories(jobProperties.getWorkRoot());
            workspace = Files.createTempDirectory(
                jobProperties.getWorkRoot(),
                ".legacy-merge-"
            );
            workspaceLockChannel = FileChannel.open(
                workspace.resolve(".active.lock"),
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE
            );
            workspaceLock = workspaceLockChannel.lock();
            legacyWorkspaceRegistry.register(workspace);
            List<MergeSource> sources = new ArrayList<>();
            for (int index = 0; index < files.size(); index++) {
                MultipartFile file = files.get(index);
                String filename = FilenameSanitizer.sanitize(
                    file.getOriginalFilename(),
                    "input-" + (index + 1) + ".pdf"
                );
                Path inputPath = workspace.resolve(String.format(
                    Locale.ROOT,
                    "input-%04d.bin",
                    index + 1
                ));
                try (InputStream input = file.getInputStream()) {
                    Files.copy(input, inputPath);
                }
                sources.add(new MergeSource(
                    index + 1,
                    inputPath,
                    filename,
                    Files.size(inputPath)
                ));
            }

            outputPath = reserveLegacyOutput("merged_", ".pdf");
            mergeEngine.merge(sources, outputPath, ignored -> {
            }, () -> {
            });

            return new PdfOperationResult(
                true,
                "PDFs merged successfully",
                outputPath.getFileName().toString()
            );
        } catch (OperationException exception) {
            deleteOutput(outputPath);
            throw new PdfProcessingException(exception.getMessage(), exception);
        } catch (Exception e) {
            deleteOutput(outputPath);
            throw new PdfProcessingException("Failed to merge PDFs: " + e.getMessage(), e);
        } finally {
            releaseWorkspaceLock(workspaceLock, workspaceLockChannel);
            legacyWorkspaceRegistry.unregister(workspace);
            deleteWorkspace(workspace);
        }
    }

    private void releaseWorkspaceLock(FileLock lock, FileChannel channel) {
        try {
            if (lock != null && lock.isValid()) {
                lock.release();
            }
        } catch (IOException exception) {
            logger.warn("Failed to release legacy merge workspace lock", exception);
        }
        try {
            if (channel != null) {
                channel.close();
            }
        } catch (IOException exception) {
            logger.warn("Failed to close legacy merge workspace lock channel", exception);
        }
    }

    private void validateLegacyMergeFiles(List<MultipartFile> files) throws PdfProcessingException {
        if (files == null || files.size() < 2 || files.size() > mergeProperties.getMaxFiles()) {
            throw new PdfProcessingException(
                "Merge requires between 2 and " + mergeProperties.getMaxFiles() + " PDF files"
            );
        }

        long totalBytes = 0;
        for (int index = 0; index < files.size(); index++) {
            MultipartFile file = files.get(index);
            String filename = FilenameSanitizer.sanitize(
                file.getOriginalFilename(),
                "input-" + (index + 1) + ".pdf"
            );
            String mediaType = file.getContentType() == null
                ? "application/octet-stream"
                : file.getContentType().toLowerCase(Locale.ROOT);
            if (file.isEmpty()
                    || !hasPdfStem(filename)
                    || !PDF_MEDIA_TYPES.contains(mediaType)) {
                throw new PdfProcessingException(
                    "Every merge input must be a non-empty PDF"
                );
            }
            try {
                totalBytes = Math.addExact(totalBytes, file.getSize());
            } catch (ArithmeticException exception) {
                throw new PdfProcessingException("Merge inputs exceed the total size limit");
            }
        }
        if (totalBytes > mergeProperties.getMaxTotalInputBytes()) {
            throw new PdfProcessingException("Merge inputs exceed the total size limit");
        }
    }

    private boolean hasPdfStem(String filename) {
        String lower = filename.toLowerCase(Locale.ROOT);
        return lower.endsWith(".pdf")
            && !filename.substring(0, filename.length() - 4).isBlank();
    }

    private void deleteOutput(Path outputPath) {
        if (outputPath == null) {
            return;
        }
        try {
            Files.deleteIfExists(outputPath);
        } catch (IOException exception) {
            logger.warn("Failed to remove partial legacy merge output {}", outputPath, exception);
        }
    }

    private void deleteWorkspace(Path workspace) {
        if (workspace == null || !Files.exists(workspace)) {
            return;
        }
        try (var paths = Files.walk(workspace)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException exception) {
                    logger.warn("Failed to remove legacy merge workspace path {}", path, exception);
                }
            });
        } catch (IOException exception) {
            logger.warn("Failed to inspect legacy merge workspace {}", workspace, exception);
        }
    }

    /**
     * Split a PDF into a deterministic ZIP artifact.
     */
    public PdfOperationResult splitPdf(
            MultipartFile file,
            String groups,
            String originalFilename) throws PdfProcessingException {
        LegacyOperationGuard guard = new LegacyOperationGuard();
        try {
            return splitPdf(file, groups, originalFilename, guard);
        } finally {
            guard.complete();
        }
    }

    public PdfOperationResult splitPdf(
            MultipartFile file,
            String groups,
            String originalFilename,
            LegacyOperationGuard guard) throws PdfProcessingException {
        guard.checkCancelled();
        validateLegacySplitGroups(groups);
        String multipartFilename = file == null ? null : file.getOriginalFilename();
        String sourceFilename = FilenameSanitizer.sanitize(
            originalFilename == null || originalFilename.isBlank()
                ? multipartFilename
                : originalFilename,
            "source.pdf"
        );
        validateLegacySplitFile(file, sourceFilename);
        Path workspace = null;
        Path outputPath = null;
        FileChannel workspaceLockChannel = null;
        FileLock workspaceLock = null;
        try {
            Files.createDirectories(jobProperties.getWorkRoot());
            workspace = Files.createTempDirectory(
                jobProperties.getWorkRoot(),
                ".legacy-split-"
            );
            workspaceLockChannel = FileChannel.open(
                workspace.resolve(".active.lock"),
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE
            );
            workspaceLock = workspaceLockChannel.lock();
            legacyWorkspaceRegistry.register(workspace);

            Path inputPath = workspace.resolve("input-0001.bin");
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, inputPath);
            }
            ObjectNode options = objectMapper.createObjectNode();
            if (groups == null || groups.isBlank()) {
                options.put("mode", "individual");
            } else {
                options.put("mode", "ranges");
                var ranges = options.putArray("ranges");
                for (String group : groups.split(";", -1)) {
                    ranges.add(group.trim());
                }
            }

            SplitResult result = splitEngine.split(
                inputPath,
                options,
                workspace,
                ignored -> {
                },
                guard::checkCancelled
            );
            List<OperationOutput> parts = new ArrayList<>(result.parts().size());
            for (SplitResult.Part part : result.parts()) {
                String suffix = groups == null || groups.isBlank()
                    ? String.format(Locale.ROOT, "_page_%04d", part.pages().getFirst())
                    : String.format(Locale.ROOT, "_part_%04d", part.position());
                parts.add(new OperationOutput(
                    part.path(),
                    FilenameSanitizer.withSuffix(sourceFilename, suffix),
                    "application/pdf"
                ));
            }
            OperationOutput zip = zipArtifactService.create(
                parts,
                workspace.resolve("split.zip"),
                "split.zip",
                Math.addExact(
                    splitProperties.getMaxTotalOutputBytes(),
                    1024L * 1024L
                ),
                guard::checkCancelled,
                true
            );
            outputPath = reserveLegacyOutput("split_", ".zip");
            guard.own(outputPath);
            Files.move(
                zip.path(),
                outputPath,
                StandardCopyOption.REPLACE_EXISTING
            );
            guard.checkCancelled();
            return new PdfOperationResult(
                true,
                "PDF split into " + result.parts().size() + " documents",
                outputPath.getFileName().toString()
            );
        } catch (OperationCancelledException exception) {
            deleteOutput(outputPath);
            throw new PdfProcessingException("Split was cancelled", exception);
        } catch (OperationException exception) {
            deleteOutput(outputPath);
            throw new PdfProcessingException(exception.getMessage(), exception);
        } catch (Exception exception) {
            deleteOutput(outputPath);
            throw new PdfProcessingException(
                "Failed to split PDF: " + exception.getMessage(),
                exception
            );
        } finally {
            releaseWorkspaceLock(workspaceLock, workspaceLockChannel);
            legacyWorkspaceRegistry.unregister(workspace);
            deleteWorkspace(workspace);
        }
    }

    private void validateLegacySplitGroups(String groups)
            throws PdfProcessingException {
        if (groups == null) {
            return;
        }
        if (groups.length() > MAX_LEGACY_SPLIT_GROUPS_LENGTH) {
            throw new PdfProcessingException(
                "Split ranges exceed the 64 KiB options limit"
            );
        }
        if (groups.isBlank()) {
            return;
        }

        int rangeCount = 1;
        for (int index = 0; index < groups.length(); index++) {
            char character = groups.charAt(index);
            if (character > 0x7f) {
                throw new PdfProcessingException(
                    "Split ranges may contain only ASCII characters"
                );
            }
            if (character == ';'
                    && ++rangeCount > splitProperties.getMaxOutputs()) {
                throw new PdfProcessingException(
                    "Split ranges exceed the "
                        + splitProperties.getMaxOutputs()
                        + " output limit"
                );
            }
        }
    }

    private void validateLegacySplitFile(
            MultipartFile file,
            String sourceFilename) throws PdfProcessingException {
        if (file == null || file.isEmpty()) {
            throw new PdfProcessingException("Split requires one non-empty PDF");
        }

        String mediaType = file.getContentType() == null
            ? "application/octet-stream"
            : file.getContentType().toLowerCase(Locale.ROOT);
        if (!hasPdfStem(sourceFilename) || !PDF_MEDIA_TYPES.contains(mediaType)) {
            throw new PdfProcessingException("Split input must be a PDF");
        }
        if (file.getSize() > mergeProperties.getMaxTotalInputBytes()) {
            throw new PdfProcessingException("Split input exceeds the total size limit");
        }
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
            tools.jackson.databind.ObjectMapper mapper = new tools.jackson.databind.ObjectMapper();
            java.util.List<java.util.Map<String, Object>> redactions = mapper.readValue(redactionsJson, 
                new tools.jackson.core.type.TypeReference<java.util.List<java.util.Map<String, Object>>>(){});
            
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
     * Get base filename from original filename or use default prefix.
     * <p>
     * This method is used to construct a new output filename from user input.
     * It sanitizes the filename to prevent path traversal by removing any
     * directory components and embedded null bytes before appending the
     * {@code operationSuffix}.
     * <p>
     * Note: For validating user-supplied filenames when downloading existing
     * files, {@code validateFilename(...)} is stricter and rejects null bytes
     * outright. Here we intentionally take a more permissive approach since we
     * are only generating a new filename, not resolving an existing one.
     */
    private String getBaseFilename(String originalFilename, String operationSuffix) {
        if (originalFilename != null && !originalFilename.isEmpty()) {
            // Sanitize: remove null bytes
            String sanitized = originalFilename.replace("\0", "");
            // Strip any directory components (both Unix and Windows separators)
            int lastSlash = sanitized.lastIndexOf('/');
            int lastBackslash = sanitized.lastIndexOf('\\');
            int lastSep = Math.max(lastSlash, lastBackslash);
            if (lastSep >= 0) {
                sanitized = sanitized.substring(lastSep + 1);
            }
            // If nothing remains after stripping separators (e.g., "///"), fall back to operationSuffix
            if (sanitized.isEmpty()) {
                return operationSuffix;
            }
            // Remove .pdf extension and add operation suffix
            String baseName = sanitized.replaceAll("\\.[pP][dD][fF]$", "");
            // Handle edge case where the filename is only an extension (e.g., ".pdf")
            if (baseName.isEmpty()) {
                return operationSuffix;
            }
            return baseName + "_" + operationSuffix;
        }
        return operationSuffix;
    }

    /**
     * Get upload directory, create if it doesn't exist
     */
    private File getUploadDir() throws IOException {
        File dir = new File(uploadDir);
        Files.createDirectories(dir.toPath());
        if (!dir.isDirectory()) {
            throw new IOException("Upload path is not a directory: " + dir);
        }
        return dir;
    }

    private Path reserveLegacyOutput(String prefix, String suffix)
            throws IOException {
        Path directory = getUploadDir().toPath();
        for (int attempt = 0; attempt < 3; attempt++) {
            Path candidate = directory.resolve(
                prefix + UUID.randomUUID() + suffix
            );
            try {
                return Files.createFile(candidate);
            } catch (FileAlreadyExistsException ignored) {
                // Retry with a new opaque identifier.
            }
        }
        throw new IOException("Could not reserve a unique output filename");
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
        
        // Disallow any path separators to prevent directory traversal via the filename
        if (filename.contains("/") || filename.contains("\\")) {
            throw new PdfProcessingException("Invalid filename: path separators are not allowed");
        }
        
        // Basic structure check: require a non-empty base name and an allowed extension
        int lastDot = filename.lastIndexOf('.');
        if (lastDot <= 0 || lastDot == filename.length() - 1) {
            throw new PdfProcessingException("Invalid filename: missing or malformed file extension");
        }
        
        String baseName = filename.substring(0, lastDot);
        String extension = filename.substring(lastDot + 1).toLowerCase();
        
        if (!extension.equals("pdf")
                && !extension.equals("md")
                && !extension.equals("docx")
                && !extension.equals("zip")) {
            throw new PdfProcessingException(
                "Invalid filename: only .pdf, .md, .docx, or .zip extensions are allowed"
            );
        }
        
        // Prevent obvious parent directory references while still allowing multiple dots in the base name
        // (e.g., allow "report..v1.pdf" but reject "../secret.pdf")
        if (baseName.equals("..")
                || baseName.startsWith(".." + File.separator)
                || baseName.contains(".." + File.separator)) {
            throw new PdfProcessingException("Invalid filename: parent directory references are not allowed");
        }
    }
    
    /**
     * Download file
     */
    public byte[] downloadFile(String filename) throws PdfProcessingException {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            streamDownloadFile(filename, output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new PdfProcessingException(
                "Failed to buffer download: " + exception.getMessage(),
                exception
            );
        }
    }

    public long getDownloadFileSize(String filename) throws PdfProcessingException {
        try {
            return Files.size(resolveDownloadPath(filename));
        } catch (IOException exception) {
            throw new PdfProcessingException(
                "Failed to read download size: " + exception.getMessage(),
                exception
            );
        }
    }

    public String getDownloadMediaType(String filename) throws PdfProcessingException {
        validateFilename(filename);
        String extension = filename.substring(filename.lastIndexOf('.') + 1)
            .toLowerCase(Locale.ROOT);
        return switch (extension) {
            case "pdf" -> "application/pdf";
            case "zip" -> "application/zip";
            case "md" -> "text/markdown";
            case "docx" ->
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            default -> "application/octet-stream";
        };
    }

    public void streamDownloadFile(
            String filename,
            OutputStream output) throws PdfProcessingException {
        try (InputStream input = Files.newInputStream(resolveDownloadPath(filename))) {
            input.transferTo(output);
        } catch (IOException exception) {
            throw new PdfProcessingException(
                "Failed to stream download: " + exception.getMessage(),
                exception
            );
        }
    }

    private Path resolveDownloadPath(String filename) throws PdfProcessingException {
        try {
            validateFilename(filename);
            Path uploadPath = Paths.get(uploadDir).toRealPath();
            Path resolvedPath = uploadPath.resolve(filename).normalize().toRealPath();
            if (!resolvedPath.startsWith(uploadPath)) {
                throw new PdfProcessingException(
                    "Access denied: file is outside the allowed directory"
                );
            }
            return resolvedPath;
        } catch (PdfProcessingException exception) {
            throw exception;
        } catch (java.nio.file.NoSuchFileException exception) {
            throw new PdfProcessingException("File not found: " + filename, exception);
        } catch (IOException exception) {
            throw new PdfProcessingException(
                "Failed to resolve download: " + exception.getMessage(),
                exception
            );
        }
    }
}
