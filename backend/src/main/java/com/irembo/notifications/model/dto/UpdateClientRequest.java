package com.irembo.notifications.model.dto;

import jakarta.validation.constraints.Size;

/**
 * Request DTO for updating a client.
 */
public record UpdateClientRequest(
        @Size(min = 16, max = 255, message = "API key must be between 16 and 255 characters")
        String apiKey,

        @Size(min = 1, max = 255, message = "Client name must be between 1 and 255 characters")
        String name,

        Integer priority,

        Boolean active
) {
}
