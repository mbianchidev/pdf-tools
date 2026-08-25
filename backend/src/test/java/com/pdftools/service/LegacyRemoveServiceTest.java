package com.pdftools.service;

import com.pdftools.config.JobProperties;
import com.pdftools.dto.PdfOperationResult;
import com.pdftools.exception.PdfProcessingException;
import com.pdftools.operations.LegacyOperationExecutor;
import com.pdftools.operations.LegacyOperationGuard;
import com.pdftools.operations.LegacyWorkspaceRegistry;
import com.pdftools.operations.remove.RemovePagePlanner;
import com.pdftools.operations.remove.RemovePdfOperation;
import com.pdftools.operations.shared.pages.PageExpressionParser;
import com.pdftools.operations.split.PdfSplitEngine;
import com.pdftools.operations.split.SplitPlanFactory;
import com.pdftools.operations.split.SplitProperties;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyRemoveServiceTest {

    @TempDir
    Path temporaryDirectory;

    private LegacyRemoveService service;

    @BeforeEach
    void setUp() {
        SplitProperties properties = new SplitProperties();
        PageExpressionParser parser = new PageExpressionParser();
        PdfSplitEngine engine = new PdfSplitEngine(
            new SplitPlanFactory(parser, properties),
            properties
        );
        RemovePdfOperation operation = new RemovePdfOperation(
            engine,
            new RemovePagePlanner(parser)
        );
        JobProperties jobProperties = new JobProperties();
        jobProperties.setWorkRoot(temporaryDirectory.resolve("work"));
        service = new LegacyRemoveService(
            new LegacyOperationExecutor(
                jobProperties,
                new LegacyWorkspaceRegistry()
            ),
            operation,
            new ObjectMapper(),
            temporaryDirectory.resolve("outputs").toString()
        );
    }

    @Test
    void removesRangesIntoOneStreamingArtifact() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "source.pdf",
            "application/pdf",
            pdf(5)
        );
        LegacyOperationGuard guard = new LegacyOperationGuard();

        PdfOperationResult result = service.removePages(
            file,
            "2,4-5",
            "source.pdf",
            guard
        );
        guard.complete();

        assertTrue(result.isSuccess());
        try (PDDocument output = Loader.loadPDF(
                temporaryDirectory.resolve("outputs")
                    .resolve(result.getOutputFilename())
                    .toFile())) {
            assertEquals(2, output.getNumberOfPages());
        }
    }

    @Test
    void rejectsDuplicateAndAllPageRemoval() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "source.pdf",
            "application/pdf",
            pdf(3)
        );

        assertThrows(
            PdfProcessingException.class,
            () -> service.removePages(
                file,
                "2,2",
                "source.pdf",
                new LegacyOperationGuard()
            )
        );
        assertThrows(
            PdfProcessingException.class,
            () -> service.removePages(
                file,
                "all",
                "source.pdf",
                new LegacyOperationGuard()
            )
        );
    }

    private byte[] pdf(int pages) throws Exception {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (int index = 0; index < pages; index++) {
                document.addPage(new PDPage());
            }
            document.save(output);
            return output.toByteArray();
        }
    }
}
