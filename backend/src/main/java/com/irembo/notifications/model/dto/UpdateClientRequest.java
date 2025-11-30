package com.irembo.notifications.model.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for updating a client.
 */
public record UpdateClientRequest(
        @Size(min = 16, max = 255, message = "API key must be between 16 and 255 characters")
        String apiKey,

        @Size(min = 1, max = 255, message = "Client name must be between 1 and 255 characters")
        String name,

        @Min(value = 0, message = "Priority must be at least 0")
        @Max(value = 100, message = "Priority must not exceed 100")
        Integer priority,

        Boolean active
) {
}
