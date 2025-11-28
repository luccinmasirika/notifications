package com.irembo.notifications.model.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for creating or updating client limits.
 */
public record ClientLimitRequest(
        @NotNull(message = "Window size in seconds is required")
        @Min(value = 1, message = "Window size must be at least 1 second")
        Integer windowSizeSeconds,

        @NotNull(message = "Max requests per window is required")
        @Min(value = 1, message = "Max requests per window must be at least 1")
        Integer maxRequestsPerWindow,

        @NotNull(message = "Monthly quota is required")
        @Min(value = 1, message = "Monthly quota must be at least 1")
        Integer monthlyQuota
) {
}
