package com.pdftools.exception;

import com.pdftools.api.ApiErrorResponse;
import com.pdftools.dto.PdfOperationResult;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiProtocolExceptionHandler {

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<?> handleUploadTooLarge(
            MaxUploadSizeExceededException exception,
            HttpServletRequest request) {
        return error(
            HttpStatus.PAYLOAD_TOO_LARGE,
            "UPLOAD_TOO_LARGE",
            "The upload exceeds the 100 MB request limit",
            request
        );
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<?> handleInvalidMultipart(
            MultipartException exception,
            HttpServletRequest request) {
        return error(
            HttpStatus.BAD_REQUEST,
            "INVALID_MULTIPART_REQUEST",
            "The multipart upload could not be processed",
            request
        );
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<?> handleUnsupportedMediaType(
            HttpMediaTypeNotSupportedException exception,
            HttpServletRequest request) {
        return error(
            HttpStatus.UNSUPPORTED_MEDIA_TYPE,
            "UNSUPPORTED_MEDIA_TYPE",
            "The request content type is not supported",
            request
        );
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<?> handleUnsupportedMethod(
            HttpRequestMethodNotSupportedException exception,
            HttpServletRequest request) {
        return error(
            HttpStatus.METHOD_NOT_ALLOWED,
            "METHOD_NOT_ALLOWED",
            "The HTTP method is not supported for this endpoint",
            request
        );
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<Void> handleNotAcceptable(
            HttpMediaTypeNotAcceptableException exception) {
        return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).build();
    }

    private ResponseEntity<?> error(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request) {
        if (request.getRequestURI().startsWith("/api/pdf")) {
            return ResponseEntity.status(status)
                .body(new PdfOperationResult(false, message, null));
        }
        return ResponseEntity.status(status).body(new ApiErrorResponse(
            Instant.now(),
            status.value(),
            code,
            message,
            request.getRequestURI(),
            Map.of()
        ));
    }
}
