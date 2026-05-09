package com.inventory.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Catches all exceptions thrown by controllers and converts them
 * into clean JSON error responses. Without this, Spring would return
 * ugly HTML error pages.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(InsufficientStockException.class)
    public ResponseEntity<Map<String, Object>> handleInsufficientStock(InsufficientStockException e) {
        return buildResponse(HttpStatus.CONFLICT, "INSUFFICIENT_STOCK", e.getMessage());
    }

    @ExceptionHandler(QuotaExceededException.class)
    public ResponseEntity<Map<String, Object>> handleQuotaExceeded(QuotaExceededException e) {
        return buildResponse(HttpStatus.TOO_MANY_REQUESTS, "QUOTA_EXCEEDED", e.getMessage());
    }

    @ExceptionHandler(RateLimitException.class)
    public ResponseEntity<Map<String, Object>> handleRateLimit(RateLimitException e) {
        return buildResponse(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", e.getMessage());
    }

    @ExceptionHandler(DataValidationException.class)
    public ResponseEntity<Map<String, Object>> handleDataValidation(DataValidationException e) {
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "DATA_VALIDATION_ERROR", e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException e) {
        return buildResponse(HttpStatus.BAD_REQUEST, "BAD_REQUEST", e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception e) {
        log.error("Unhandled exception", e);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", e.getMessage());
    }

    private ResponseEntity<Map<String, Object>> buildResponse(HttpStatus status, String code, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "error");
        body.put("code", code);
        body.put("message", message);
        body.put("timestamp", Instant.now().toString());
        return ResponseEntity.status(status).body(body);
    }
}
