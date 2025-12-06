package com.irembo.notifications.model.dto;

import com.irembo.notifications.infra.db.entity.Client;

import java.time.LocalDateTime;

/**
 * Response DTO for client operations.
 * Includes the API key and API secret in plain text ONLY when it's been created.
 * This is the ONLY time they will be visible - they're not stored in plain text.
 */
public record ClientResponse(
        Long id,
        String apiKey, // Only present when created/updated with new API key
        String apiSecret, // Only present when created (HMAC authentication)
        String name,
        Integer priority,
        Boolean active,
        String authMethod,
        String status,
        LocalDateTime createdAt,
        String warning // Warning message about API key/secret visibility
) {
    /**
     * Create a response with API key and secret (for new clients with HMAC).
     */
    public static ClientResponse withApiKeyAndSecret(Client client, String plainApiKey, String plainApiSecret) {
        return new ClientResponse(
                client.getId(),
                plainApiKey,
                plainApiSecret,
                client.getName(),
                client.getPriority(),
                client.getActive(),
                client.getAuthMethod() != null ? client.getAuthMethod() : "HMAC",
                client.getStatus() != null ? client.getStatus() : "ACTIVE",
                client.getCreatedAt(),
                "⚠️ IMPORTANT: Save both API key and secret now. They will not be shown again. The secret cannot be recovered."
        );
    }

    /**
     * Create a response with API key (for new clients or when API key is updated).
     */
    public static ClientResponse withApiKey(Client client, String plainApiKey) {
        return new ClientResponse(
                client.getId(),
                plainApiKey,
                null,
                client.getName(),
                client.getPriority(),
                client.getActive(),
                client.getAuthMethod() != null ? client.getAuthMethod() : "HMAC",
                client.getStatus() != null ? client.getStatus() : "ACTIVE",
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
                null,
                client.getName(),
                client.getPriority(),
                client.getActive(),
                client.getAuthMethod() != null ? client.getAuthMethod() : "HMAC",
                client.getStatus() != null ? client.getStatus() : "ACTIVE",
                client.getCreatedAt(),
                null
        );
    }
}

