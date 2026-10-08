package com.figurestore.api.error;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> details = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> details.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return response(HttpStatus.BAD_REQUEST, "Validasi request gagal", details, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> unreadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "Body request tidak valid", Map.of(), request);
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ApiError> status(ResponseStatusException ex, HttpServletRequest request) {
        return response(HttpStatus.valueOf(ex.getStatusCode().value()), ex.getReason(), Map.of(), request);
    }

    @ExceptionHandler(BadCredentialsException.class)
    ResponseEntity<ApiError> credentials(BadCredentialsException ex, HttpServletRequest request) {
        return response(HttpStatus.UNAUTHORIZED, "Username atau password salah", Map.of(), request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> accessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return response(HttpStatus.FORBIDDEN, "Akses ditolak", Map.of(), request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled request error", ex);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "Terjadi kesalahan internal", Map.of(), request);
    }

    private ResponseEntity<ApiError> response(HttpStatus status, String message,
                                               Map<String, String> details, HttpServletRequest request) {
        ApiError body = new ApiError(Instant.now(), status.value(), status.getReasonPhrase(), message,
                request.getRequestURI(), (String) request.getAttribute("requestId"), details);
        return ResponseEntity.status(status).body(body);
    }
}
