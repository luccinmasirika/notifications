package com.irembo.notifications.service;

import com.irembo.notifications.config.NotificationProcessingConfig;
import com.irembo.notifications.config.RabbitMQConfig;
import com.irembo.notifications.infra.db.entity.Notification;
import com.irembo.notifications.infra.db.repository.NotificationRepository;
import com.irembo.notifications.model.dto.NotificationMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Notification Consumer.
 * Consumes notifications from RabbitMQ queue and processes them asynchronously.
 * Simulates sending with a random delay (1-3 seconds by default).
 */
@Service
public class NotificationConsumer {

    private static final Logger logger = LoggerFactory.getLogger(NotificationConsumer.class);

    @Value("${app.filter.masking.recipient-visible-chars:3}")
    private int recipientVisibleChars;

    private final NotificationRepository notificationRepository;
    private final NotificationProcessingConfig processingConfig;

    public NotificationConsumer(
            NotificationRepository notificationRepository,
            NotificationProcessingConfig processingConfig) {
        this.notificationRepository = notificationRepository;
        this.processingConfig = processingConfig;
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_NAME)
    @Transactional
    public void processNotification(NotificationMessage message) {
        Long notificationId = message.notificationId();
        logger.info("Processing notification {} for client '{}' (ID: {}): channel={}, to={}",
                notificationId,
                message.clientName(),
                message.clientId(),
                message.channel(),
                maskRecipientForLogging(message.recipient()));

        // Find notification in database
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new RuntimeException("Notification not found: " + notificationId));

        try {
            // Update status to PROCESSING
            notification.setStatus("PROCESSING");
            notificationRepository.save(notification);
            logger.debug("Notification {} status updated to PROCESSING", notificationId);

            // Generate processing delay between min and max
            int delayMs = processingConfig.generateProcessingDelay();
            logger.info("Notification {} will be processed after {}ms delay", notificationId, delayMs);

            // Simulate processing delay
            Thread.sleep(delayMs);

            // Simulate sending notification (log only, no real provider)
            logger.info("Simulating sending notification {} via {} to {}",
                    notificationId,
                    message.channel(),
                    maskRecipientForLogging(message.recipient()));

            // Update status to SENT
            notification.setStatus("SENT");
            notification.setProcessedAt(LocalDateTime.now());
            notificationRepository.save(notification);

            logger.info("Notification {} successfully processed and marked as SENT", notificationId);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Notification {} processing interrupted", notificationId, e);
            updateNotificationStatus(notification, "FAILED", "Processing interrupted: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Failed to process notification {}: {}", notificationId, e.getMessage(), e);
            updateNotificationStatus(notification, "FAILED", "Processing error: " + e.getMessage());
        }
    }

    private void updateNotificationStatus(Notification notification, String status, String errorMessage) {
        notification.setStatus(status);
        notification.setProcessedAt(LocalDateTime.now());
        notification.setErrorMessage(errorMessage);
        notificationRepository.save(notification);
    }

    /**
     * Mask recipient for logging (privacy).
     * Shows first N and last N characters.
     */
    private String maskRecipientForLogging(String recipient) {
        int minLength = recipientVisibleChars * 2;
        if (recipient == null || recipient.length() <= minLength) {
            return "***";
        }
        return recipient.substring(0, recipientVisibleChars) + "***" + recipient.substring(recipient.length() - recipientVisibleChars);
    }
}
