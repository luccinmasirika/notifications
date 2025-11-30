package com.irembo.notifications.model.dto;

import com.irembo.notifications.infra.db.entity.Client;

import java.time.LocalDateTime;

/**
 * Response DTO for client operations.
 * Includes the API key in plain text ONLY when it's been created or updated.
 * This is the ONLY time the API key will be visible - it's not stored in plain text.
 */
public record ClientResponse(
        Long id,
        String apiKey, // Only present when created/updated with new API key
        String name,
        Integer priority,
        Boolean active,
        LocalDateTime createdAt,
        String warning // Warning message about API key visibility
) {
    /**
     * Create a response with API key (for new clients or when API key is updated).
     */
    public static ClientResponse withApiKey(Client client, String plainApiKey) {
        return new ClientResponse(
                client.getId(),
                plainApiKey,
                client.getName(),
                client.getPriority(),
                client.getActive(),
                client.getCreatedAt(),
                "⚠️ IMPORTANT: Save this API key now. It will not be shown again and cannot be recovered."
        );
    }

    /**
     * Create a response without API key (for updates that don't change the API key).
     */
    public static ClientResponse withoutApiKey(Client client) {
        return new ClientResponse(
                client.getId(),
                null,
                client.getName(),
                client.getPriority(),
                client.getActive(),
                client.getCreatedAt(),
                null
        );
    }
}

