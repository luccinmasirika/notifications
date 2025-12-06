package com.irembo.notifications.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.irembo.notifications.model.enums.NotificationChannel;

public record NotificationMessage(
        @JsonProperty("notificationId")
        Long notificationId,

        @JsonProperty("clientId")
        Long clientId,

        @JsonProperty("clientName")
        String clientName,

        @JsonProperty("channel")
        NotificationChannel channel,

        @JsonProperty("recipient")
        String recipient,

        @JsonProperty("message")
        String message
) {
}
