package com.seqscreen.controller;

import com.seqscreen.validation.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Map;

/**
 * Every rejected request answers with {@code {"error": "..."}} and a status the UI can act on,
 * so a bad motif, a missing dataset and an oversized upload read differently.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private final long maxUploadMb;

    public GlobalExceptionHandler(@Value("${MAX_UPLOAD_MB:200}") long maxUploadMb) {
        this.maxUploadMb = maxUploadMb;
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, String>> handleApi(ApiException error) {
        return ResponseEntity.status(error.status()).body(Map.of("error", error.getMessage()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> handleTooLarge(MaxUploadSizeExceededException error) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(Map.of("error", "The upload is larger than this instance's "
                        + maxUploadMb + " MB limit."));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleState(IllegalStateException error) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", error.getMessage() == null ? "The search failed." : error.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleArgument(IllegalArgumentException error) {
        return ResponseEntity.badRequest()
                .body(Map.of("error", error.getMessage() == null ? "Invalid request." : error.getMessage()));
    }
}
