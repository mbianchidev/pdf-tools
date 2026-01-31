package com.pdftools.controller;

import com.pdftools.dto.PdfOperationResult;
import com.pdftools.exception.PdfProcessingException;
import com.pdftools.service.PdfService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.stream.Collectors;
import java.util.Arrays;

@RestController
@RequestMapping("/api/pdf")
public class PdfController {

    @Autowired
    private PdfService pdfService;

    @PostMapping("/merge")
    public ResponseEntity<PdfOperationResult> mergePdfs(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(value = "originalFilename", required = false) String originalFilename) throws PdfProcessingException {
        PdfOperationResult result = pdfService.mergePdfs(files, originalFilename);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/split")
    public ResponseEntity<PdfOperationResult> splitPdf(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "groups", required = false) String groups,
            @RequestParam(value = "originalFilename", required = false) String originalFilename) throws PdfProcessingException {
        PdfOperationResult result = pdfService.splitPdf(file, groups, originalFilename);
        return ResponseEntity.ok(result);
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
    public ResponseEntity<PdfOperationResult> removePages(
            @RequestParam("file") MultipartFile file,
            @RequestParam("pages") String pages,
            @RequestParam(value = "originalFilename", required = false) String originalFilename) throws PdfProcessingException {
        List<Integer> pageNumbers = Arrays.stream(pages.split(","))
            .map(String::trim)
            .map(Integer::parseInt)
            .collect(Collectors.toList());
        PdfOperationResult result = pdfService.removePages(file, pageNumbers, originalFilename);
        return ResponseEntity.ok(result);
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
    public ResponseEntity<byte[]> downloadFile(@PathVariable String filename) 
            throws PdfProcessingException {
        byte[] fileContent = pdfService.downloadFile(filename);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment", filename);
        
        return new ResponseEntity<>(fileContent, headers, HttpStatus.OK);
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("PDF Tools API is running");
    }
}
