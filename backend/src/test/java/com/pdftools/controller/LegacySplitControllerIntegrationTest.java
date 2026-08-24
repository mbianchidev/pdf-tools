package com.pdftools.controller;

import com.pdftools.api.MultipartTextPartReader;
import com.pdftools.exception.LegacyExceptionHandler;
import com.pdftools.service.PdfService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockPart;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayOutputStream;
import java.time.Duration;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class LegacySplitControllerIntegrationTest {

    @Test
    void rejectsOversizedTextPartsBeforeStringBinding() throws Exception {
        MockMvc mockMvc = standaloneSetup(new PdfController(
            mock(PdfService.class),
            new MultipartTextPartReader(),
            mock(AsyncTaskExecutor.class),
            Duration.ofMinutes(10)
        ))
            .setControllerAdvice(new LegacyExceptionHandler())
            .build();
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "source.pdf",
            "application/pdf",
            onePagePdf()
        );
        MockPart groups = new MockPart("groups", new byte[65_537]);

        mockMvc.perform(
            multipart("/api/pdf/split")
                .file(file)
                .part(groups)
        )
            .andExpect(status().isPayloadTooLarge())
            .andExpect(jsonPath("$.success").value(false));
    }

    private byte[] onePagePdf() throws Exception {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(output);
            return output.toByteArray();
        }
    }
}
