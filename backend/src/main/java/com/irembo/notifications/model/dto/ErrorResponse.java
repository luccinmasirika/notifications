package com.irembo.notifications.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        String error,
        String message,
        int status,
        Instant timestamp,
        String path
) {
    public static ErrorResponse of(String error, String message, int status, String path) {
        return new ErrorResponse(error, message, status, Instant.now(), path);
    }

    public static ErrorResponse of(String error, String message, int status) {
        return new ErrorResponse(error, message, status, Instant.now(), null);
    }
}
