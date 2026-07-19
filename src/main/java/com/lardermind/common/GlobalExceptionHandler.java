package com.lardermind.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

import java.io.IOException;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final ObjectMapper objectMapper;

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnauthorized(UnauthorizedException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(BadRequestException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ex.getMessage()));
    }

    /**
     * Client closed the connection (common with SSE). Do not attempt to write a JSON body —
     * the response is often already committed as {@code text/event-stream}.
     */
    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public void handleAsyncClientDisconnect(AsyncRequestNotUsableException ex) {
        log.debug("Async client disconnect: {}", ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public void handleGeneral(Exception ex, WebRequest request, HttpServletResponse response) throws IOException {
        if (response.isCommitted() || isClientDisconnect(ex) || isEventStreamResponse(request)) {
            log.debug("Suppressing error response for disconnected/SSE client: {}", ex.toString());
            return;
        }
        response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), ApiResponse.error(ex.getMessage()));
    }

    private static boolean isClientDisconnect(Throwable error) {
        Throwable current = error;
        while (current != null) {
            String name = current.getClass().getName();
            String message = current.getMessage() != null ? current.getMessage() : "";
            if (name.contains("AsyncRequestNotUsableException")
                    || name.contains("ClientAbortException")
                    || message.contains("Broken pipe")
                    || message.contains("Connection reset")
                    || message.contains("disconnected client")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static boolean isEventStreamResponse(WebRequest request) {
        if (!(request instanceof ServletWebRequest servletRequest)) {
            return false;
        }
        String contentType = servletRequest.getResponse() != null
                ? servletRequest.getResponse().getContentType()
                : null;
        return contentType != null && contentType.startsWith(MediaType.TEXT_EVENT_STREAM_VALUE);
    }

    // ── Custom Exception Classes ──

    public static class ResourceNotFoundException extends RuntimeException {
        public ResourceNotFoundException(String message) { super(message); }
    }

    public static class UnauthorizedException extends RuntimeException {
        public UnauthorizedException(String message) { super(message); }
    }

    public static class BadRequestException extends RuntimeException {
        public BadRequestException(String message) { super(message); }
    }
}
