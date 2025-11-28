package com.irembo.notifications.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for creating a new client.
 */
public record CreateClientRequest(
        @NotBlank(message = "API key is required")
        @Size(min = 16, max = 255, message = "API key must be between 16 and 255 characters")
        String apiKey,

        @NotBlank(message = "Client name is required")
        @Size(min = 1, max = 255, message = "Client name must be between 1 and 255 characters")
        String name,

        Integer priority,

        Boolean active
) {
    public CreateClientRequest {
        // Set defaults if not provided
        if (priority == null) {
            priority = 0;
        }
        if (active == null) {
            active = true;
        }
    }
}
