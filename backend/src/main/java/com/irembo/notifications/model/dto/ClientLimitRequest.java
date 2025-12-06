package com.irembo.notifications.model.dto;

import com.irembo.notifications.validation.ValidThresholds;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@ValidThresholds
public record ClientLimitRequest(
        @NotNull(message = "Window size in seconds is required")
        @Min(value = 1, message = "Window size must be at least 1 second")
        Integer windowSizeSeconds,

        @NotNull(message = "Max requests per window is required")
        @Min(value = 1, message = "Max requests per window must be at least 1")
        Integer maxRequestsPerWindow,

        @NotNull(message = "Monthly quota is required")
        @Min(value = 1, message = "Monthly quota must be at least 1")
        Integer monthlyQuota,

        @DecimalMin(value = "0.0", message = "Soft throttle threshold must be at least 0.0")
        @DecimalMax(value = "1.0", message = "Soft throttle threshold must not exceed 1.0")
        Double softThrottleThreshold,

        @DecimalMin(value = "0.0", message = "Hard reject threshold must be at least 0.0")
        @DecimalMax(value = "1.0", message = "Hard reject threshold must not exceed 1.0")
        Double hardRejectThreshold
) {
}
