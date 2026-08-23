package com.pdftools.exception;

import com.pdftools.api.ApiErrorResponse;
import com.pdftools.api.ApiException;
import com.pdftools.jobs.api.JobController;
import com.pdftools.operations.OperationException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.time.Instant;
import java.util.Map;

@RestControllerAdvice(assignableTypes = JobController.class)
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiErrorResponse> handleApiException(
            ApiException exception,
            HttpServletRequest request) {
        return response(
            exception.getStatus(),
            exception.getCode(),
            exception.getMessage(),
            request,
            exception.getDetails()
        );
    }

    @ExceptionHandler(OperationException.class)
    public ResponseEntity<ApiErrorResponse> handleOperationException(
            OperationException exception,
            HttpServletRequest request) {
        return response(
            HttpStatus.UNPROCESSABLE_ENTITY,
            exception.getCode(),
            exception.getMessage(),
            request,
            exception.getDetails()
        );
    }

    @ExceptionHandler(PdfProcessingException.class)
    public ResponseEntity<ApiErrorResponse> handlePdfProcessingException(
            PdfProcessingException exception,
            HttpServletRequest request) {
        logger.warn("PDF processing rejected: {}", exception.getMessage());
        return response(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "PDF_PROCESSING_FAILED",
            exception.getMessage(),
            request,
            Map.of()
        );
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleMaxUploadSizeExceededException(
            MaxUploadSizeExceededException exception,
            HttpServletRequest request) {
        logger.warn("Upload exceeded configured limit: {}", exception.getMessage());
        return response(
            HttpStatus.PAYLOAD_TOO_LARGE,
            "UPLOAD_TOO_LARGE",
            "The upload exceeds the configured size limit",
            request,
            Map.of()
        );
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiErrorResponse> handleMultipartException(
            MultipartException exception,
            HttpServletRequest request) {
        logger.warn("Invalid multipart request: {}", exception.getMessage());
        return response(
            HttpStatus.BAD_REQUEST,
            "INVALID_MULTIPART_REQUEST",
            "The multipart upload could not be processed",
            request,
            Map.of()
        );
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingParams(
            MissingServletRequestParameterException exception,
            HttpServletRequest request) {
        return response(
            HttpStatus.BAD_REQUEST,
            "MISSING_PARAMETER",
            "Missing required parameter: " + exception.getParameterName(),
            request,
            Map.of("parameter", exception.getParameterName())
        );
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingPart(
            MissingServletRequestPartException exception,
            HttpServletRequest request) {
        return response(
            HttpStatus.BAD_REQUEST,
            "MISSING_MULTIPART_PART",
            "Missing required multipart field: " + exception.getRequestPartName(),
            request,
            Map.of("part", exception.getRequestPartName())
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableMessage(
            HttpMessageNotReadableException exception,
            HttpServletRequest request) {
        return response(
            HttpStatus.BAD_REQUEST,
            "INVALID_REQUEST_BODY",
            "The request body could not be read",
            request,
            Map.of()
        );
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException exception,
            HttpServletRequest request) {
        return response(
            HttpStatus.BAD_REQUEST,
            "INVALID_PARAMETER_TYPE",
            "Invalid value for parameter: " + exception.getName(),
            request,
            Map.of("parameter", exception.getName())
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgumentException(
            IllegalArgumentException exception,
            HttpServletRequest request) {
        return response(
            HttpStatus.BAD_REQUEST,
            "INVALID_INPUT",
            exception.getMessage(),
            request,
            Map.of()
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGenericException(
            Exception exception,
            HttpServletRequest request) {
        logger.error("Unexpected request failure", exception);
        return response(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "INTERNAL_ERROR",
            "An unexpected error occurred while processing the request",
            request,
            Map.of()
        );
    }

    private ResponseEntity<ApiErrorResponse> response(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request,
            Map<String, Object> details) {
        return ResponseEntity.status(status).body(new ApiErrorResponse(
            Instant.now(),
            status.value(),
            code,
            message,
            request.getRequestURI(),
            details
        ));
    }
}
