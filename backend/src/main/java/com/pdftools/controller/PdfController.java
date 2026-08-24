package com.pdftools.controller;

import com.pdftools.api.MultipartTextPartReader;
import com.pdftools.dto.PdfOperationResult;
import com.pdftools.exception.PdfProcessingException;
import com.pdftools.operations.LegacyOperationGuard;
import com.pdftools.service.PdfService;
import com.pdftools.service.LegacyRemoveService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.context.request.async.WebAsyncTask;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Arrays;
import java.util.concurrent.Callable;

@RestController
@RequestMapping("/api/pdf")
public class PdfController {

    private static final int MAX_SPLIT_GROUPS_BYTES = 65_536;
    private static final int MAX_PAGE_EXPRESSION_BYTES = 4_096;
    private static final int MAX_ORIGINAL_FILENAME_BYTES = 1_024;

    private final PdfService pdfService;
    private final MultipartTextPartReader textPartReader;
    private final AsyncTaskExecutor taskExecutor;
    private final Duration splitTimeout;
    private final LegacyRemoveService legacyRemoveService;

    public PdfController(
            PdfService pdfService,
            LegacyRemoveService legacyRemoveService,
            MultipartTextPartReader textPartReader,
            @Qualifier("legacyPdfExecutor") AsyncTaskExecutor taskExecutor,
            @Value("${pdf.operations.split.legacy-timeout:10m}")
            Duration splitTimeout) {
        this.pdfService = pdfService;
        this.legacyRemoveService = legacyRemoveService;
        this.textPartReader = textPartReader;
        this.taskExecutor = taskExecutor;
        this.splitTimeout = splitTimeout;
    }

    @PostMapping("/merge")
    public ResponseEntity<PdfOperationResult> mergePdfs(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(value = "originalFilename", required = false) String originalFilename) throws PdfProcessingException {
        PdfOperationResult result = pdfService.mergePdfs(files, originalFilename);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/split")
    public WebAsyncTask<ResponseEntity<PdfOperationResult>> splitPdf(
            HttpServletRequest request,
            @RequestParam("file") MultipartFile file) throws PdfProcessingException {
        String groups = textPartReader.read(
            request,
            "groups",
            MAX_SPLIT_GROUPS_BYTES,
            false
        );
        String originalFilename = textPartReader.read(
            request,
            "originalFilename",
            MAX_ORIGINAL_FILENAME_BYTES,
            false
        );
        LegacyOperationGuard guard = new LegacyOperationGuard();
        return legacyTask(
            "Split",
            guard,
            () -> pdfService.splitPdf(
                file,
                groups,
                originalFilename,
                guard
            )
        );
    }

    @PostMapping("/extract")
    public ResponseEntity<PdfOperationResult> extractPages(
            @RequestParam("file") MultipartFile file,
            @RequestParam("pages") String pages,
            @RequestParam(value = "originalFilename", required = false) String originalFilename) throws PdfProcessingException {
        List<Integer> pageNumbers = Arrays.stream(pages.split(","))
            .map(String::trim)
            .map(Integer::parseInt)
            .collect(Collectors.toList());
        PdfOperationResult result = pdfService.extractPages(file, pageNumbers, originalFilename);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/remove")
    public WebAsyncTask<ResponseEntity<PdfOperationResult>> removePages(
            HttpServletRequest request,
            @RequestParam("file") MultipartFile file) {
        String pages = textPartReader.read(
            request,
            "pages",
            MAX_PAGE_EXPRESSION_BYTES,
            true
        );
        String originalFilename = textPartReader.read(
            request,
            "originalFilename",
            MAX_ORIGINAL_FILENAME_BYTES,
            false
        );
        LegacyOperationGuard guard = new LegacyOperationGuard();
        return legacyTask(
            "Remove Pages",
            guard,
            () -> legacyRemoveService.removePages(
                file,
                pages,
                originalFilename,
                guard
            )
        );
    }

    private WebAsyncTask<ResponseEntity<PdfOperationResult>> legacyTask(
            String operationName,
            LegacyOperationGuard guard,
            Callable<PdfOperationResult> operation) {
        WebAsyncTask<ResponseEntity<PdfOperationResult>> task =
            new WebAsyncTask<>(
                splitTimeout.toMillis(),
                taskExecutor,
                () -> ResponseEntity.ok(operation.call())
            );
        task.onTimeout(() -> {
            guard.cancel();
            return ResponseEntity
                .status(HttpStatus.REQUEST_TIMEOUT)
                .body(new PdfOperationResult(
                    false,
                    operationName
                        + " exceeded the legacy processing deadline",
                    null
                ));
        });
        task.onError(() -> {
            guard.cancel();
            return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new PdfOperationResult(
                    false,
                    operationName + " was interrupted before completion",
                    null
                ));
        });
        task.onCompletion(guard::complete);
        return task;
    }

    @PostMapping("/watermark")
    public ResponseEntity<PdfOperationResult> addWatermark(
            @RequestParam("file") MultipartFile file,
            @RequestParam("text") String watermarkText,
            @RequestParam(value = "x", required = false) Float x,
            @RequestParam(value = "y", required = false) Float y,
            @RequestParam(value = "rotation", defaultValue = "45") float rotation,
            @RequestParam(value = "opacity", defaultValue = "0.3") float opacity,
            @RequestParam(value = "originalFilename", required = false) String originalFilename) throws PdfProcessingException {
        PdfOperationResult result = pdfService.addWatermark(file, watermarkText, x, y, rotation, opacity, originalFilename);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/add-text")
    public ResponseEntity<PdfOperationResult> addText(
            @RequestParam("file") MultipartFile file,
            @RequestParam("text") String text,
            @RequestParam(value = "x", defaultValue = "50") float x,
            @RequestParam(value = "y", defaultValue = "750") float y,
            @RequestParam(value = "page", defaultValue = "1") int pageNum,
            @RequestParam(value = "fontSize", defaultValue = "12") float fontSize,
            @RequestParam(value = "fontName", defaultValue = "HELVETICA") String fontName,
            @RequestParam(value = "fontColor", defaultValue = "#000000") String fontColor,
            @RequestParam(value = "originalFilename", required = false) String originalFilename) throws PdfProcessingException {
        PdfOperationResult result = pdfService.addText(file, text, x, y, pageNum, fontSize, fontName, fontColor, originalFilename);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/add-signature")
    public ResponseEntity<PdfOperationResult> addSignature(
            @RequestParam("file") MultipartFile pdfFile,
            @RequestParam("signature") MultipartFile signatureFile,
            @RequestParam(value = "x", defaultValue = "400") float x,
            @RequestParam(value = "y", defaultValue = "100") float y,
            @RequestParam(value = "page", defaultValue = "1") int pageNum,
            @RequestParam(value = "originalFilename", required = false) String originalFilename) throws PdfProcessingException {
        PdfOperationResult result = pdfService.addSignature(pdfFile, signatureFile, x, y, pageNum, originalFilename);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/redact")
    public ResponseEntity<PdfOperationResult> redactText(
            @RequestParam("file") MultipartFile file,
            @RequestParam("x") float x,
            @RequestParam("y") float y,
            @RequestParam("width") float width,
            @RequestParam("height") float height,
            @RequestParam(value = "page", defaultValue = "1") int pageNum,
            @RequestParam(value = "originalFilename", required = false) String originalFilename) throws PdfProcessingException {
        PdfOperationResult result = pdfService.redactText(file, x, y, width, height, pageNum, originalFilename);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/redact-multiple")
    public ResponseEntity<PdfOperationResult> redactMultiple(
            @RequestParam("file") MultipartFile file,
            @RequestParam("redactions") String redactionsJson,
            @RequestParam(value = "originalFilename", required = false) String originalFilename) throws PdfProcessingException {
        PdfOperationResult result = pdfService.redactMultiple(file, redactionsJson, originalFilename);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/convert/markdown")
    public ResponseEntity<PdfOperationResult> convertToMarkdown(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "originalFilename", required = false) String originalFilename) throws PdfProcessingException {
        PdfOperationResult result = pdfService.convertToMarkdown(file, originalFilename);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/convert/docx")
    public ResponseEntity<PdfOperationResult> convertToDocx(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "originalFilename", required = false) String originalFilename) throws PdfProcessingException {
        PdfOperationResult result = pdfService.convertToDocx(file, originalFilename);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/info")
    public ResponseEntity<PdfOperationResult> getPdfInfo(
            @RequestParam("file") MultipartFile file) throws PdfProcessingException {
        PdfOperationResult result = pdfService.getPdfInfo(file);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/download/{filename}")
    public ResponseEntity<StreamingResponseBody> downloadFile(@PathVariable String filename)
            throws PdfProcessingException {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(
            pdfService.getDownloadMediaType(filename)
        ));
        headers.setContentLength(pdfService.getDownloadFileSize(filename));
        headers.setContentDisposition(ContentDisposition.attachment()
            .filename(filename, StandardCharsets.UTF_8)
            .build());
        StreamingResponseBody body = output -> {
            try {
                pdfService.streamDownloadFile(filename, output);
            } catch (PdfProcessingException exception) {
                throw new IOException("Failed to stream legacy download", exception);
            }
        };
        return new ResponseEntity<>(body, headers, HttpStatus.OK);
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("PDF Tools API is running");
    }
}
