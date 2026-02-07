package com.pdftools.service;

import com.pdftools.dto.PdfOperationResult;
import com.pdftools.exception.PdfProcessingException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PdfServiceTest {

    private PdfService pdfService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        pdfService = new PdfService();
        ReflectionTestUtils.setField(pdfService, "uploadDir", tempDir.toString());
    }

    /**
     * Helper method to create a valid PDF file for testing
     */
    private byte[] createValidPdf(int pageCount) throws IOException {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            for (int i = 0; i < pageCount; i++) {
                document.addPage(new PDPage());
            }
            document.save(baos);
            return baos.toByteArray();
        }
    }

    /**
     * Helper method to create a valid PNG signature image
     */
    private byte[] createValidSignatureImage() throws IOException {
        BufferedImage image = new BufferedImage(100, 50, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", baos);
        return baos.toByteArray();
    }

    @Test
    @DisplayName("Service should initialize correctly")
    void testServiceInitialization() {
        assertNotNull(pdfService);
    }

    @Test
    @DisplayName("Upload directory should be created")
    void testUploadDirectoryCreation() {
        File dir = new File(tempDir.toString());
        assertTrue(dir.exists());
        assertTrue(dir.isDirectory());
    }

    @Nested
    @DisplayName("Merge PDF Tests")
    class MergePdfTests {

        @Test
        @DisplayName("Should merge multiple PDFs successfully")
        void testMergePdfs_Success() throws Exception {
            byte[] pdf1 = createValidPdf(2);
            byte[] pdf2 = createValidPdf(3);

            MockMultipartFile file1 = new MockMultipartFile("file", "test1.pdf", "application/pdf", pdf1);
            MockMultipartFile file2 = new MockMultipartFile("file", "test2.pdf", "application/pdf", pdf2);

            PdfOperationResult result = pdfService.mergePdfs(Arrays.asList(file1, file2), "test1.pdf");

            assertTrue(result.isSuccess());
            assertEquals("PDFs merged successfully", result.getMessage());
            assertNotNull(result.getOutputFilename());
            assertTrue(result.getOutputFilename().contains("merged"));
        }

        @Test
        @DisplayName("Should throw exception for invalid PDF files")
        void testMergePdfs_InvalidFile() {
            MockMultipartFile invalidFile = new MockMultipartFile(
                "file", "test.pdf", "application/pdf", "invalid".getBytes());

            assertThrows(PdfProcessingException.class, () -> {
                pdfService.mergePdfs(Arrays.asList(invalidFile), "test.pdf");
            });
        }
    }

    @Nested
    @DisplayName("Split PDF Tests")
    class SplitPdfTests {

        @Test
        @DisplayName("Should split PDF into separate pages")
        void testSplitPdf_Success() throws Exception {
            byte[] pdf = createValidPdf(3);
            MockMultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", pdf);

            PdfOperationResult result = pdfService.splitPdf(file, null, "test.pdf");

            assertTrue(result.isSuccess());
            assertTrue(result.getMessage().contains("3 documents"));
            assertNotNull(result.getOutputFilename());
            String[] filenames = result.getOutputFilename().split(",");
            assertEquals(3, filenames.length);
        }

        @Test
        @DisplayName("Should throw exception for invalid PDF")
        void testSplitPdf_InvalidFile() {
            MockMultipartFile invalidFile = new MockMultipartFile(
                "file", "test.pdf", "application/pdf", "invalid".getBytes());

            assertThrows(PdfProcessingException.class, () -> {
                pdfService.splitPdf(invalidFile, null, "test.pdf");
            });
        }
    }

    @Nested
    @DisplayName("Extract Pages Tests")
    class ExtractPagesTests {

        @Test
        @DisplayName("Should extract specific pages from PDF")
        void testExtractPages_Success() throws Exception {
            byte[] pdf = createValidPdf(5);
            MockMultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", pdf);
            List<Integer> pages = Arrays.asList(1, 3, 5);

            PdfOperationResult result = pdfService.extractPages(file, pages, "test.pdf");

            assertTrue(result.isSuccess());
            assertEquals("Pages extracted successfully", result.getMessage());
            assertNotNull(result.getOutputFilename());
        }

        @Test
        @DisplayName("Should handle out of range pages gracefully")
        void testExtractPages_OutOfRange() throws Exception {
            byte[] pdf = createValidPdf(3);
            MockMultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", pdf);
            List<Integer> pages = Arrays.asList(1, 10); // Page 10 doesn't exist

            PdfOperationResult result = pdfService.extractPages(file, pages, "test.pdf");

            assertTrue(result.isSuccess());
        }
    }

    @Nested
    @DisplayName("Remove Pages Tests")
    class RemovePagesTests {

        @Test
        @DisplayName("Should remove specific pages from PDF")
        void testRemovePages_Success() throws Exception {
            byte[] pdf = createValidPdf(5);
            MockMultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", pdf);
            List<Integer> pages = Arrays.asList(2, 4);

            PdfOperationResult result = pdfService.removePages(file, pages, "test.pdf");

            assertTrue(result.isSuccess());
            assertEquals("Pages removed successfully", result.getMessage());
            assertNotNull(result.getOutputFilename());
        }
    }

    @Nested
    @DisplayName("Watermark Tests")
    class WatermarkTests {

        @Test
        @DisplayName("Should add watermark to all pages")
        void testAddWatermark_Success() throws Exception {
            byte[] pdf = createValidPdf(3);
            MockMultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", pdf);

            PdfOperationResult result = pdfService.addWatermark(file, "CONFIDENTIAL", null, null, 45f, 0.3f, "test.pdf");

            assertTrue(result.isSuccess());
            assertEquals("Watermark added successfully", result.getMessage());
            assertNotNull(result.getOutputFilename());
            assertTrue(result.getOutputFilename().contains("watermarked"));
        }
    }

    @Nested
    @DisplayName("Add Text Tests")
    class AddTextTests {

        @Test
        @DisplayName("Should add text at specified position")
        void testAddText_Success() throws Exception {
            byte[] pdf = createValidPdf(1);
            MockMultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", pdf);

            PdfOperationResult result = pdfService.addText(file, "Hello World", 100f, 500f, 1, 12f, "HELVETICA", "#000000", "test.pdf");

            assertTrue(result.isSuccess());
            assertEquals("Text added successfully", result.getMessage());
            assertNotNull(result.getOutputFilename());
        }

        @Test
        @DisplayName("Should throw exception for invalid page number")
        void testAddText_InvalidPage() throws Exception {
            byte[] pdf = createValidPdf(1);
            MockMultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", pdf);

            assertThrows(PdfProcessingException.class, () -> {
                pdfService.addText(file, "Hello", 100f, 500f, 5, 12f, "HELVETICA", "#000000", "test.pdf");
            });
        }
    }

    @Nested
    @DisplayName("Add Signature Tests")
    class AddSignatureTests {

        @Test
        @DisplayName("Should add signature image to PDF")
        void testAddSignature_Success() throws Exception {
            byte[] pdf = createValidPdf(1);
            byte[] signature = createValidSignatureImage();

            MockMultipartFile pdfFile = new MockMultipartFile("file", "test.pdf", "application/pdf", pdf);
            MockMultipartFile sigFile = new MockMultipartFile("signature", "sig.png", "image/png", signature);

            PdfOperationResult result = pdfService.addSignature(pdfFile, sigFile, 400f, 100f, 1, "test.pdf");

            assertTrue(result.isSuccess());
            assertEquals("Signature added successfully", result.getMessage());
            assertNotNull(result.getOutputFilename());
        }

        @Test
        @DisplayName("Should throw exception for invalid page number")
        void testAddSignature_InvalidPage() throws Exception {
            byte[] pdf = createValidPdf(1);
            byte[] signature = createValidSignatureImage();

            MockMultipartFile pdfFile = new MockMultipartFile("file", "test.pdf", "application/pdf", pdf);
            MockMultipartFile sigFile = new MockMultipartFile("signature", "sig.png", "image/png", signature);

            assertThrows(PdfProcessingException.class, () -> {
                pdfService.addSignature(pdfFile, sigFile, 400f, 100f, 10, "test.pdf");
            });
        }
    }

    @Nested
    @DisplayName("Redact Tests")
    class RedactTests {

        @Test
        @DisplayName("Should redact area on PDF")
        void testRedactText_Success() throws Exception {
            byte[] pdf = createValidPdf(1);
            MockMultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", pdf);

            PdfOperationResult result = pdfService.redactText(file, 100f, 700f, 200f, 30f, 1, "test.pdf");

            assertTrue(result.isSuccess());
            assertEquals("Content redacted successfully", result.getMessage());
            assertNotNull(result.getOutputFilename());
        }

        @Test
        @DisplayName("Should throw exception for invalid page number")
        void testRedactText_InvalidPage() throws Exception {
            byte[] pdf = createValidPdf(1);
            MockMultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", pdf);

            assertThrows(PdfProcessingException.class, () -> {
                pdfService.redactText(file, 100f, 700f, 200f, 30f, 10, "test.pdf");
            });
        }
    }

    @Nested
    @DisplayName("Convert to Markdown Tests")
    class ConvertToMarkdownTests {

        @Test
        @DisplayName("Should convert PDF to Markdown")
        void testConvertToMarkdown_Success() throws Exception {
            byte[] pdf = createValidPdf(1);
            MockMultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", pdf);

            PdfOperationResult result = pdfService.convertToMarkdown(file, "test.pdf");

            assertTrue(result.isSuccess());
            assertEquals("PDF converted to Markdown", result.getMessage());
            assertNotNull(result.getOutputFilename());
            assertTrue(result.getOutputFilename().endsWith(".md"));
        }
    }

    @Nested
    @DisplayName("Convert to DOCX Tests")
    class ConvertToDocxTests {

        @Test
        @DisplayName("Should convert PDF to DOCX")
        void testConvertToDocx_Success() throws Exception {
            byte[] pdf = createValidPdf(1);
            MockMultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", pdf);

            PdfOperationResult result = pdfService.convertToDocx(file, "test.pdf");

            assertTrue(result.isSuccess());
            assertEquals("PDF converted to DOCX", result.getMessage());
            assertNotNull(result.getOutputFilename());
            assertTrue(result.getOutputFilename().endsWith(".docx"));
        }
    }

    @Nested
    @DisplayName("Get PDF Info Tests")
    class GetPdfInfoTests {

        @Test
        @DisplayName("Should return PDF page count")
        void testGetPdfInfo_Success() throws Exception {
            byte[] pdf = createValidPdf(5);
            MockMultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", pdf);

            PdfOperationResult result = pdfService.getPdfInfo(file);

            assertTrue(result.isSuccess());
            assertTrue(result.getMessage().contains("5"));
        }

        @Test
        @DisplayName("Should throw exception for invalid PDF")
        void testGetPdfInfo_InvalidFile() {
            MockMultipartFile invalidFile = new MockMultipartFile(
                "file", "test.pdf", "application/pdf", "invalid content".getBytes());

            assertThrows(PdfProcessingException.class, () -> {
                pdfService.getPdfInfo(invalidFile);
            });
        }
    }

    @Nested
    @DisplayName("Download File Tests")
    class DownloadFileTests {

        @Test
        @DisplayName("Should download existing file")
        void testDownloadFile_Success() throws Exception {
            // Create a test file
            Path testFile = tempDir.resolve("test.pdf");
            Files.write(testFile, createValidPdf(1));

            byte[] result = pdfService.downloadFile("test.pdf");

            assertNotNull(result);
            assertTrue(result.length > 0);
        }

        @Test
        @DisplayName("Should throw exception for non-existent file")
        void testDownloadFile_NonExistent() {
            assertThrows(PdfProcessingException.class, () -> {
                pdfService.downloadFile("nonexistent.pdf");
            });
        }
        
        @Test
        @DisplayName("Should reject path traversal with ../")
        void testDownloadFile_PathTraversal_ParentDirectory() {
            PdfProcessingException exception = assertThrows(PdfProcessingException.class, () -> {
                pdfService.downloadFile("../etc/passwd");
            });
            assertTrue(exception.getMessage().contains("path traversal"));
        }
        
        @Test
        @DisplayName("Should reject path traversal with ..")
        void testDownloadFile_PathTraversal_DoubleDot() {
            PdfProcessingException exception = assertThrows(PdfProcessingException.class, () -> {
                pdfService.downloadFile("..\\..\\..\\windows\\system32\\config\\sam");
            });
            assertTrue(exception.getMessage().contains("path traversal"));
        }
        
        @Test
        @DisplayName("Should reject absolute paths with /")
        void testDownloadFile_AbsolutePath_Unix() {
            PdfProcessingException exception = assertThrows(PdfProcessingException.class, () -> {
                pdfService.downloadFile("/etc/passwd");
            });
            assertTrue(exception.getMessage().contains("path traversal"));
        }
        
        @Test
        @DisplayName("Should reject absolute paths with \\")
        void testDownloadFile_AbsolutePath_Windows() {
            PdfProcessingException exception = assertThrows(PdfProcessingException.class, () -> {
                pdfService.downloadFile("C:\\Windows\\System32\\config\\sam");
            });
            assertTrue(exception.getMessage().contains("path traversal"));
        }
        
        @Test
        @DisplayName("Should reject null byte injection")
        void testDownloadFile_NullByteInjection() {
            PdfProcessingException exception = assertThrows(PdfProcessingException.class, () -> {
                pdfService.downloadFile("test.pdf\0.jpg");
            });
            assertTrue(exception.getMessage().contains("null byte"));
        }
        
        @Test
        @DisplayName("Should reject null filename")
        void testDownloadFile_NullFilename() {
            PdfProcessingException exception = assertThrows(PdfProcessingException.class, () -> {
                pdfService.downloadFile(null);
            });
            assertTrue(exception.getMessage().contains("null or empty"));
        }
        
        @Test
        @DisplayName("Should reject empty filename")
        void testDownloadFile_EmptyFilename() {
            PdfProcessingException exception = assertThrows(PdfProcessingException.class, () -> {
                pdfService.downloadFile("");
            });
            assertTrue(exception.getMessage().contains("null or empty"));
        }
        
        @Test
        @DisplayName("Should reject whitespace-only filename")
        void testDownloadFile_WhitespaceFilename() {
            PdfProcessingException exception = assertThrows(PdfProcessingException.class, () -> {
                pdfService.downloadFile("   ");
            });
            assertTrue(exception.getMessage().contains("null or empty"));
        }
        
        @Test
        @DisplayName("Should reject invalid characters in filename")
        void testDownloadFile_InvalidCharacters() {
            PdfProcessingException exception = assertThrows(PdfProcessingException.class, () -> {
                pdfService.downloadFile("test<>.pdf");
            });
            assertTrue(exception.getMessage().contains("alphanumeric"));
        }
        
        @Test
        @DisplayName("Should reject invalid file extensions")
        void testDownloadFile_InvalidExtension() {
            PdfProcessingException exception = assertThrows(PdfProcessingException.class, () -> {
                pdfService.downloadFile("malicious.exe");
            });
            assertTrue(exception.getMessage().contains("extension"));
        }
        
        @Test
        @DisplayName("Should allow valid .pdf files")
        void testDownloadFile_ValidPdfFilename() throws Exception {
            Path testFile = tempDir.resolve("valid-file_123.pdf");
            Files.write(testFile, createValidPdf(1));

            byte[] result = pdfService.downloadFile("valid-file_123.pdf");

            assertNotNull(result);
            assertTrue(result.length > 0);
        }
        
        @Test
        @DisplayName("Should allow valid .md files")
        void testDownloadFile_ValidMdFilename() throws Exception {
            Path testFile = tempDir.resolve("document.md");
            Files.write(testFile, "# Test".getBytes());

            byte[] result = pdfService.downloadFile("document.md");

            assertNotNull(result);
            assertTrue(result.length > 0);
        }
        
        @Test
        @DisplayName("Should allow valid .docx files")
        void testDownloadFile_ValidDocxFilename() throws Exception {
            Path testFile = tempDir.resolve("document.docx");
            Files.write(testFile, new byte[]{1, 2, 3, 4});

            byte[] result = pdfService.downloadFile("document.docx");

            assertNotNull(result);
            assertTrue(result.length > 0);
        }
    }
}
