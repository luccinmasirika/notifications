package com.irembo.notifications.service;

import com.irembo.notifications.model.dto.NotificationRequest;
import com.irembo.notifications.model.dto.NotificationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Notification Service.
 * Handles the business logic for sending notifications.
 *
 * This is a stub implementation that simulates notification sending.
 * In production, this would integrate with actual SMS/Email providers.
 */
@Service
public class NotificationService {

    private static final Logger logger = LoggerFactory.getLogger(NotificationService.class);

    /**
     * Send a notification.
     *
     * Currently simulates sending by logging the notification.
     * In production, this would:
     * 1. Queue the notification in a message broker (Kafka, RabbitMQ)
     * 2. Worker processes would consume and send via SMS/Email providers
     * 3. Store delivery status in database
     *
     * @param request Notification request details
     * @param clientId ID of the authenticated client
     * @param clientName Name of the authenticated client
     * @return NotificationResponse with acceptance confirmation
     */
    public NotificationResponse sendNotification(
            NotificationRequest request,
            Long clientId,
            String clientName) {

        // Log notification acceptance
        logger.info("Notification accepted for client '{}' (ID: {}): channel={}, to={}, messageLength={}",
                clientName,
                clientId,
                request.channel(),
                maskRecipient(request.to()),
                request.message().length());

        // In production, would queue for async processing:
        // - kafkaTemplate.send("notifications", notificationEvent);
        // - Or: rabbitTemplate.convertAndSend("notifications.exchange", notification);

        // Simulate validation based on channel
        validateChannel(request);

        // Return acceptance response
        return NotificationResponse.accepted(
                request.channel(),
                request.to(),
                clientName
        );
    }

    /**
     * Validate channel-specific requirements.
     * This is where you'd add channel-specific logic.
     */
    private void validateChannel(NotificationRequest request) {
        switch (request.channel()) {
            case SMS -> validateSmsRecipient(request.to());
            case EMAIL -> validateEmailRecipient(request.to());
        }
    }

    /**
     * Validate SMS recipient format.
     * Basic validation - in production, use a proper phone number library.
     */
    private void validateSmsRecipient(String phoneNumber) {
        if (!phoneNumber.startsWith("+")) {
            logger.warn("SMS recipient missing country code prefix: {}", maskRecipient(phoneNumber));
        }
        // Additional validation could be added here
        // e.g., libphonenumber library for proper validation
    }

    /**
     * Validate email recipient format.
     * Basic validation - Spring's @Email annotation handles this better.
     */
    private void validateEmailRecipient(String email) {
        if (!email.contains("@")) {
            logger.warn("Email recipient appears invalid: {}", maskRecipient(email));
        }
        // Additional validation could be added here
    }

    /**
     * Mask recipient for logging (privacy).
     * Shows first 3 and last 3 characters.
     */
    private String maskRecipient(String recipient) {
        if (recipient == null || recipient.length() <= 6) {
            return "***";
        }
        return recipient.substring(0, 3) + "***" + recipient.substring(recipient.length() - 3);
    }
}
