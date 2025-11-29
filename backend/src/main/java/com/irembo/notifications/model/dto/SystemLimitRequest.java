package com.irembo.notifications.model.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for creating or updating system limits.
 */
public record SystemLimitRequest(
        @NotNull(message = "Name is required")
        @Size(min = 1, max = 255, message = "Name must be between 1 and 255 characters")
        @Pattern(
                regexp = "^[a-zA-Z0-9_]+$",
                message = "Name must contain only letters, numbers, and underscores (format: limit_123)"
        )
        String name,

        @NotNull(message = "Window size in seconds is required")
        @Min(value = 1, message = "Window size must be at least 1 second")
        Integer windowSizeSeconds,

        @NotNull(message = "Max requests per window is required")
        @Min(value = 1, message = "Max requests per window must be at least 1")
        Integer maxRequestsPerWindow,

        Boolean active
) {
    public SystemLimitRequest {
        // Set default if not provided
        if (active == null) {
            active = true;
        }
    }
}

