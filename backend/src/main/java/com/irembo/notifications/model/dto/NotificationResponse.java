package com.irembo.notifications.model.dto;

import com.irembo.notifications.model.enums.NotificationChannel;

import java.time.Instant;

public record NotificationResponse(
        String status,
        String message,
        NotificationChannel channel,
        String recipient,
        Instant timestamp,
        String clientName
) {
    public static NotificationResponse accepted(
            NotificationChannel channel,
            String recipient,
            String clientName) {
        return new NotificationResponse(
                "accepted",
                "Notification queued for delivery",
                channel,
                recipient,
                Instant.now(),
                clientName
        );
    }
}
