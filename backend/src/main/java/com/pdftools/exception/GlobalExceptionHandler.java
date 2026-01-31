package com.pdftools.exception;

import com.pdftools.dto.PdfOperationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(PdfProcessingException.class)
    public ResponseEntity<PdfOperationResult> handlePdfProcessingException(PdfProcessingException ex) {
        logger.error("PDF processing error: {}", ex.getMessage(), ex);
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(new PdfOperationResult(false, ex.getMessage(), null));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<PdfOperationResult> handleMaxUploadSizeExceededException(
            MaxUploadSizeExceededException ex) {
        logger.error("File size exceeded: {}", ex.getMessage());
        return ResponseEntity
            .status(HttpStatus.PAYLOAD_TOO_LARGE)
            .body(new PdfOperationResult(false, 
                "File size exceeds maximum allowed size of 100MB. Please upload a smaller file.", null));
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<PdfOperationResult> handleMultipartException(MultipartException ex) {
        logger.error("Multipart error: {}", ex.getMessage(), ex);
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(new PdfOperationResult(false, 
                "Error processing file upload. Please ensure you're uploading valid PDF files.", null));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<PdfOperationResult> handleMissingParams(
            MissingServletRequestParameterException ex) {
        logger.error("Missing parameter: {}", ex.getParameterName());
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(new PdfOperationResult(false, 
                String.format("Missing required parameter: %s", ex.getParameterName()), null));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<PdfOperationResult> handleIllegalArgumentException(IllegalArgumentException ex) {
        logger.error("Invalid argument: {}", ex.getMessage(), ex);
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(new PdfOperationResult(false, 
                "Invalid input: " + ex.getMessage(), null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<PdfOperationResult> handleGenericException(Exception ex) {
        logger.error("Unexpected error: {}", ex.getMessage(), ex);
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new PdfOperationResult(false, 
                "An unexpected error occurred while processing your request. Please try again.", null));
    }
}
