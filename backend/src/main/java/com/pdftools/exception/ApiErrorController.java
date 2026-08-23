package com.pdftools.exception;

import com.pdftools.api.ApiErrorResponse;
import com.pdftools.dto.PdfOperationResult;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
public class ApiErrorController implements ErrorController {

    @RequestMapping("/error")
    public ResponseEntity<?> error(HttpServletRequest request) {
        HttpStatus status = resolveStatus(request);
        String originalPath = resolveOriginalPath(request);
        String message = status == HttpStatus.NOT_FOUND
            ? "The requested endpoint does not exist"
            : "The request could not be completed";

        if (originalPath.startsWith("/api/pdf")) {
            return ResponseEntity.status(status)
                .body(new PdfOperationResult(false, message, null));
        }

        String code = status == HttpStatus.NOT_FOUND ? "ROUTE_NOT_FOUND" : "HTTP_ERROR";
        return ResponseEntity.status(status).body(new ApiErrorResponse(
            Instant.now(),
            status.value(),
            code,
            message,
            originalPath,
            Map.of()
        ));
    }

    private HttpStatus resolveStatus(HttpServletRequest request) {
        Object value = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        if (value instanceof Integer statusCode) {
            HttpStatus status = HttpStatus.resolve(statusCode);
            if (status != null) {
                return status;
            }
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private String resolveOriginalPath(HttpServletRequest request) {
        Object value = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        return value instanceof String path ? path : request.getRequestURI();
    }
}
