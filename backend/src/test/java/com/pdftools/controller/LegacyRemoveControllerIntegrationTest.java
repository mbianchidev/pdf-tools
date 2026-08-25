package com.pdftools.controller;

import com.pdftools.api.MultipartTextPartReader;
import com.pdftools.config.JobProperties;
import com.pdftools.exception.LegacyExceptionHandler;
import com.pdftools.operations.LegacyOperationExecutor;
import com.pdftools.operations.LegacyWorkspaceRegistry;
import com.pdftools.operations.remove.RemovePagePlanner;
import com.pdftools.operations.remove.RemovePdfOperation;
import com.pdftools.operations.shared.pages.PageExpressionParser;
import com.pdftools.operations.split.PdfSplitEngine;
import com.pdftools.operations.split.SplitPlanFactory;
import com.pdftools.operations.split.SplitProperties;
import com.pdftools.service.LegacyRemoveService;
import com.pdftools.service.PdfService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockPart;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.time.Duration;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class LegacyRemoveControllerIntegrationTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void mapsPreExecutionValidationFailuresToBadRequest() throws Exception {
        LegacyRemoveService removeService = removeService();
        AsyncTaskExecutor directExecutor = Runnable::run;
        MockMvc mockMvc = standaloneSetup(new PdfController(
            mock(PdfService.class),
            removeService,
            new MultipartTextPartReader(),
            directExecutor,
            Duration.ofMinutes(10)
        ))
            .setControllerAdvice(new LegacyExceptionHandler())
            .build();
        MockMultipartFile invalidFile = new MockMultipartFile(
            "file",
            "source.txt",
            "text/plain",
            "not-pdf".getBytes()
        );

        MvcResult pending = mockMvc.perform(
            multipart("/api/pdf/remove")
                .file(invalidFile)
                .part(new MockPart("pages", "1".getBytes()))
        )
            .andExpect(request().asyncStarted())
            .andReturn();

        mockMvc.perform(asyncDispatch(pending))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(
            multipart("/api/pdf/remove")
                .part(new MockPart("pages", "1".getBytes()))
        )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false));
    }

    private LegacyRemoveService removeService() {
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
        JobProperties jobs = new JobProperties();
        jobs.setWorkRoot(temporaryDirectory.resolve("work"));
        return new LegacyRemoveService(
            new LegacyOperationExecutor(
                jobs,
                new LegacyWorkspaceRegistry()
            ),
            operation,
            new ObjectMapper(),
            temporaryDirectory.resolve("outputs").toString()
        );
    }
}
