package com.pdftools.exception;

import com.pdftools.dto.PdfOperationResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(PdfProcessingException.class)
    public ResponseEntity<PdfOperationResult> handlePdfProcessingException(PdfProcessingException ex) {
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(new PdfOperationResult(false, ex.getMessage(), null));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<PdfOperationResult> handleMaxUploadSizeExceededException(
            MaxUploadSizeExceededException ex) {
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(new PdfOperationResult(false, "File size exceeds maximum allowed size", null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<PdfOperationResult> handleGenericException(Exception ex) {
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new PdfOperationResult(false, "An error occurred: " + ex.getMessage(), null));
    }
}
