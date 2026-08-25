package com.pdftools.exception;

import com.pdftools.api.MultipartTextPartException;
import com.pdftools.controller.PdfController;
import com.pdftools.dto.PdfOperationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestControllerAdvice(assignableTypes = PdfController.class)
public class LegacyExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(LegacyExceptionHandler.class);

    @ExceptionHandler(PdfProcessingException.class)
    public ResponseEntity<PdfOperationResult> handlePdfProcessingException(
            PdfProcessingException exception) {
        logger.warn("Legacy PDF processing rejected: {}", exception.getMessage());
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(new PdfOperationResult(false, exception.getMessage(), null));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<PdfOperationResult> handleMaxUploadSizeExceededException(
            MaxUploadSizeExceededException exception) {
        logger.warn("Legacy upload exceeded configured limit: {}", exception.getMessage());
        return ResponseEntity
            .status(HttpStatus.PAYLOAD_TOO_LARGE)
            .body(new PdfOperationResult(
                false,
                "File size exceeds the maximum allowed size.",
                null
            ));
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<PdfOperationResult> handleMultipartException(
            MultipartException exception) {
        logger.warn("Invalid legacy multipart request: {}", exception.getMessage());
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(new PdfOperationResult(
                false,
                "Error processing file upload. Please upload valid PDF files.",
                null
            ));
    }

    @ExceptionHandler(MultipartTextPartException.class)
    public ResponseEntity<PdfOperationResult> handleMultipartTextPartException(
            MultipartTextPartException exception) {
        logger.warn(
            "Invalid legacy multipart field {}: {}",
            exception.getPartName(),
            exception.getMessage()
        );
        return ResponseEntity
            .status(exception.getStatus())
            .body(new PdfOperationResult(false, exception.getMessage(), null));
    }

    @ExceptionHandler(TaskRejectedException.class)
    public ResponseEntity<PdfOperationResult> handleTaskRejectedException(
            TaskRejectedException exception) {
        logger.warn("Legacy PDF worker queue is full: {}", exception.getMessage());
        return ResponseEntity
            .status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(new PdfOperationResult(
                false,
                "The PDF worker queue is full. Try again shortly.",
                null
            ));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<PdfOperationResult> handleMissingParameter(
            MissingServletRequestParameterException exception) {
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(new PdfOperationResult(
                false,
                "Missing required parameter: " + exception.getParameterName(),
                null
            ));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<PdfOperationResult> handleMissingPart(
            MissingServletRequestPartException exception) {
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(new PdfOperationResult(
                false,
                "Missing required multipart part: "
                    + exception.getRequestPartName(),
                null
            ));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<PdfOperationResult> handleIllegalArgumentException(
            IllegalArgumentException exception) {
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(new PdfOperationResult(
                false,
                "Invalid input: " + exception.getMessage(),
                null
            ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<PdfOperationResult> handleUnexpectedException(Exception exception) {
        logger.error("Unexpected legacy request failure", exception);
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new PdfOperationResult(
                false,
                "An unexpected error occurred while processing your request. Please try again.",
                null
            ));
    }
}
