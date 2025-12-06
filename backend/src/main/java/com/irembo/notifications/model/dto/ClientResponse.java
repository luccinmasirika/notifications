package com.irembo.notifications.model.dto;

import com.irembo.notifications.infra.db.entity.Client;

import java.time.LocalDateTime;

public record ClientResponse(
        Long id,
        String apiKey,
        String apiSecret,
        String name,
        Integer priority,
        Boolean active,
        String authMethod,
        String status,
        LocalDateTime createdAt,
        String warning
) {
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

