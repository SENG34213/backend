package com.gamingcastle.userservice.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        String error,
        String message,
        int status,
        String path,
        Instant timestamp,
        Map<String, String> fieldErrors
) {

    public static ErrorResponse of(String error, String message, HttpStatus status, String path) {
        return new ErrorResponse(error, message, status.value(), path, Instant.now(), null);
    }

    public static ErrorResponse of(String error,
                                   String message,
                                   HttpStatus status,
                                   String path,
                                   Map<String, String> fieldErrors) {
        return new ErrorResponse(error, message, status.value(), path, Instant.now(),
                (fieldErrors == null || fieldErrors.isEmpty()) ? null : fieldErrors);
    }
}