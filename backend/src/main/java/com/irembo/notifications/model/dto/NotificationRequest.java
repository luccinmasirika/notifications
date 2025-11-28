package com.irembo.notifications.model.dto;

import com.irembo.notifications.model.enums.NotificationChannel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record NotificationRequest(
        @NotNull(message = "Channel is required")
        NotificationChannel channel,

        @NotBlank(message = "Recipient is required")
        String to,

        @NotBlank(message = "Message is required")
        @Size(max = 500, message = "Message must not exceed 500 characters")
        String message
) {
}
