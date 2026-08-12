package com.example.app.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.io.IOException;
import java.time.Instant;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationExceptions(MethodArgumentNotValidException ex) {
        String errors = ex.getBindingResult().getFieldErrors()
                .stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));

        return buildResponse(HttpStatus.BAD_REQUEST, "INVALID_INPUT", errors);
    }

    /**
     * Handle client disconnect (broken pipe, connection reset) gracefully.
     * These are expected when users close the browser/tab during SSE streaming.
     * Don't log at ERROR level — it's noise, not a server problem.
     */
    @ExceptionHandler(IOException.class)
    public ResponseEntity<Void> handleIOException(IOException ex,
            HttpServletRequest request,
            HttpServletResponse response) {
        String msg = ex.getMessage();
        if (msg != null && (msg.contains("Broken pipe") || msg.contains("Connection reset"))) {
            log.warn("[CLIENT_DISCONNECT] Client disconnected during request: {} {}",
                    request.getRequestURI());
        } else {
            log.warn("[IO_ERROR] IOException: {}", msg);
        }
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleRuntimeException(RuntimeException ex,
            HttpServletRequest request,
            HttpServletResponse response) {
        // If response is already committed (e.g., SSE streaming started),
        // Spring can't serialize an ErrorResponse as text/event-stream.
        // Just close the connection instead of trying to write JSON.
        if (response.isCommitted() || isSseRequest(request)) {
            log.warn("[SSE_ERROR] Runtime exception during SSE: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
        log.error("Runtime exception occurred: ", ex);
        if (ex.getMessage().contains("not found")) {
            return buildResponse(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage());
        }
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneralExceptions(Exception ex,
            HttpServletRequest request,
            HttpServletResponse response) {
        // If response is already committed or the request was SSE,
        // Spring can't serialize ErrorResponse as text/event-stream.
        if (response.isCommitted() || isSseRequest(request)) {
            log.warn("[SSE_ERROR] Exception during SSE: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
        log.error("Unhandled exception occurred: {}", ex.getMessage());
        if (ex instanceof org.springframework.web.HttpMediaTypeNotAcceptableException) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR",
                "An unexpected error occurred. Please try again later.");
    }

    /**
     * Check if the request targets an SSE endpoint by examining the Accept header
     * or the request URI pattern.
     */
    private boolean isSseRequest(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        if (accept != null && accept.contains("text/event-stream")) {
            return true;
        }
        String uri = request.getRequestURI();
        return uri != null && (uri.contains("/chat/stream") || uri.contains("/notifications/stream"));
    }

    private ResponseEntity<ErrorResponse> buildResponse(HttpStatus status, String code, String message) {
        ErrorResponse response = ErrorResponse.builder()
                .code(code)
                .message(message)
                .timestamp(Instant.now().toEpochMilli())
                .build();
        return new ResponseEntity<>(response, status);
    }
}
