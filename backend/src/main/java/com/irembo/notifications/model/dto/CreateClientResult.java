package com.irembo.notifications.model.dto;

import com.irembo.notifications.infra.db.entity.Client;

public record CreateClientResult(
        Client client,
        String apiSecret
) {
}
