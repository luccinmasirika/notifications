package com.irembo.notifications.model.dto;

import com.irembo.notifications.infra.db.entity.Client;

/**
 * Result of creating a new client.
 * Contains the created client entity and the plain text API secret.
 * The API secret should be shown to the user once and then stored securely.
 */
public record CreateClientResult(
        Client client,
        String apiSecret
) {
}
