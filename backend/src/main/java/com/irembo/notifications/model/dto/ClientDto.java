package com.irembo.notifications.model.dto;

import com.irembo.notifications.infra.db.entity.Client;

import java.time.LocalDateTime;

/**
 * Client DTO for API responses.
 * Does NOT include apiKeyHash for security reasons.
 */
public record ClientDto(
        Long id,
        String name,
        Integer priority,
        Boolean active,
        LocalDateTime createdAt
) {
    /**
     * Create a ClientDto from a Client entity, excluding apiKeyHash.
     */
    public static ClientDto fromClient(Client client) {
        return new ClientDto(
                client.getId(),
                client.getName(),
                client.getPriority(),
                client.getActive(),
                client.getCreatedAt()
        );
    }
}

