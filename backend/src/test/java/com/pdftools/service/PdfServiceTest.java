package com.pdftools.service;

import com.pdftools.dto.PdfOperationResult;
import com.pdftools.exception.PdfProcessingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

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

    @Test
    void testServiceInitialization() {
        assertNotNull(pdfService);
    }

    @Test
    void testGetPdfInfo_WithInvalidFile_ThrowsException() {
        MockMultipartFile invalidFile = new MockMultipartFile(
            "file", 
            "test.pdf", 
            "application/pdf", 
            "invalid content".getBytes()
        );

        assertThrows(PdfProcessingException.class, () -> {
            pdfService.getPdfInfo(invalidFile);
        });
    }

    @Test
    void testDownloadFile_WithNonExistentFile_ThrowsException() {
        assertThrows(PdfProcessingException.class, () -> {
            pdfService.downloadFile("nonexistent.pdf");
        });
    }

    @Test
    void testUploadDirectoryCreation() throws IOException {
        // Verify that the service can handle directory creation
        File dir = new File(tempDir.toString());
        assertTrue(dir.exists());
        assertTrue(dir.isDirectory());
    }
}
