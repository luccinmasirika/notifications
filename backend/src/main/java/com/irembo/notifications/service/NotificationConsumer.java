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

        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new RuntimeException("Notification not found: " + notificationId));

        try {
            notification.setStatus("PROCESSING");
            notificationRepository.save(copyNotification(notification));
            logger.debug("Notification {} status updated to PROCESSING", notificationId);

            int delayMs = processingConfig.generateProcessingDelay();
            logger.info("Notification {} will be processed after {}ms delay", notificationId, delayMs);

            Thread.sleep(delayMs);

            logger.info("Simulating sending notification {} via {} to {}",
                    notificationId,
                    message.channel(),
                    maskRecipientForLogging(message.recipient()));

            notification.setStatus("SENT");
            notification.setProcessedAt(LocalDateTime.now());
            notificationRepository.save(copyNotification(notification));

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
        try {
            notificationRepository.save(copyNotification(notification));
        } catch (Exception saveError) {
            logger.error("Failed to persist notification {} status update: {}", notification.getId(), saveError.getMessage());
        }
    }

    private Notification copyNotification(Notification source) {
        Notification copy = new Notification();
        copy.setId(source.getId());
        copy.setClientId(source.getClientId());
        copy.setClientName(source.getClientName());
        copy.setChannel(source.getChannel());
        copy.setRecipient(source.getRecipient());
        copy.setMessage(source.getMessage());
        copy.setStatus(source.getStatus());
        copy.setCreatedAt(source.getCreatedAt());
        copy.setProcessedAt(source.getProcessedAt());
        copy.setErrorMessage(source.getErrorMessage());
        return copy;
    }

    private String maskRecipientForLogging(String recipient) {
        int minLength = recipientVisibleChars * 2;
        if (recipient == null || recipient.length() <= minLength) {
            return "***";
        }
        return recipient.substring(0, recipientVisibleChars) + "***" + recipient.substring(recipient.length() - recipientVisibleChars);
    }
}
