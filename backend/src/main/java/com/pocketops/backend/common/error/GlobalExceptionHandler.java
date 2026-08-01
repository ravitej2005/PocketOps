package com.pocketops.backend.common.error;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<ApiErrorResponse> handleValidation(Exception exception, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiErrorResponse(
                        ErrorCode.VALIDATION_ERROR.name(),
                        "Validation failed.",
                        requestId(request)
                ));
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiErrorResponse> handleApiException(ApiException exception, HttpServletRequest request) {
        return ResponseEntity.status(exception.getStatus())
                .body(new ApiErrorResponse(
                        exception.getCode().name(),
                        exception.getMessage(),
                        requestId(request)
                ));
    }

    @ExceptionHandler({BadCredentialsException.class, AuthenticationException.class})
    public ResponseEntity<ApiErrorResponse> handleAuthFailures(RuntimeException exception, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiErrorResponse(
                        ErrorCode.AUTHENTICATION_REQUIRED.name(),
                        "Authentication required.",
                        requestId(request)
                ));
    }

    @ExceptionHandler({IllegalStateException.class, DataAccessException.class})
    public ResponseEntity<ApiErrorResponse> handleServiceErrors(RuntimeException exception, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiErrorResponse(
                        ErrorCode.INTERNAL_ERROR.name(),
                        "An unexpected error occurred.",
                        requestId(request)
                ));
    }

    private String requestId(HttpServletRequest request) {
        Object value = request.getAttribute(RequestIdContext.ATTRIBUTE);
        return value instanceof String ? (String) value : "unknown";
    }
}
