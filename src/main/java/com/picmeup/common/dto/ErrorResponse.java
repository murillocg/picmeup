package com.picmeup.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        int status,
        String message,
        Map<String, String> fieldErrors,
        Instant timestamp
) {
    public ErrorResponse(int status, String message) {
        this(status, message, null, Instant.now());
    }

    public ErrorResponse(int status, String message, Map<String, String> fieldErrors) {
        this(status, message, fieldErrors, Instant.now());
    }
}
