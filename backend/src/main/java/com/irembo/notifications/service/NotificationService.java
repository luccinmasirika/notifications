package com.irembo.notifications.service;

import com.irembo.notifications.config.RabbitMQConfig;
import com.irembo.notifications.infra.db.entity.Notification;
import com.irembo.notifications.infra.db.repository.NotificationRepository;
import com.irembo.notifications.model.dto.NotificationMessage;
import com.irembo.notifications.model.dto.NotificationRequest;
import com.irembo.notifications.model.dto.NotificationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

    private static final Logger logger = LoggerFactory.getLogger(NotificationService.class);

    private final RabbitTemplate rabbitTemplate;
    private final NotificationRepository notificationRepository;

    @Value("${app.filter.masking.recipient-visible-chars:3}")
    private int recipientVisibleChars;

    public NotificationService(RabbitTemplate rabbitTemplate, NotificationRepository notificationRepository) {
        this.rabbitTemplate = rabbitTemplate;
        this.notificationRepository = notificationRepository;
    }

    @Transactional
    public NotificationResponse queueNotification(
            NotificationRequest request,
            Long clientId,
            String clientName) {

        validateChannelRequest(request);

        Notification notification = new Notification();
        notification.setClientId(clientId);
        notification.setClientName(clientName);
        notification.setChannel(request.channel());
        notification.setRecipient(request.to());
        notification.setMessage(request.message());
        notification.setStatus("PENDING");

        Notification savedNotification = notificationRepository.save(notification);
        logger.info("Notification created with ID {} for client '{}' (ID: {}): channel={}, to={}, messageLength={}",
                savedNotification.getId(),
                clientName,
                clientId,
                request.channel(),
                maskRecipientForLogging(request.to()),
                request.message().length());

        try {
            NotificationMessage message = new NotificationMessage(
                    savedNotification.getId(),
                    clientId,
                    clientName,
                    request.channel(),
                    request.to(),
                    request.message()
            );

            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.EXCHANGE_NAME,
                    RabbitMQConfig.ROUTING_KEY,
                    message
            );

            logger.info("Notification {} published to RabbitMQ queue", savedNotification.getId());

        } catch (Exception e) {
            logger.error("Failed to publish notification {} to RabbitMQ: {}", savedNotification.getId(), e.getMessage(), e);
            savedNotification.setStatus("FAILED");
            savedNotification.setErrorMessage("Failed to publish to queue: " + e.getMessage());
            notificationRepository.save(savedNotification);
            throw new RuntimeException("Failed to queue notification", e);
        }

        return NotificationResponse.accepted(
                request.channel(),
                request.to(),
                clientName
        );
    }

    private void validateChannelRequest(NotificationRequest request) {
        switch (request.channel()) {
            case SMS -> validateSmsRecipient(request.to());
            case EMAIL -> validateEmailRecipient(request.to());
        }
    }

    private void validateSmsRecipient(String phoneNumber) {
        if (!phoneNumber.startsWith("+")) {
            logger.warn("SMS recipient missing country code prefix: {}", maskRecipientForLogging(phoneNumber));
        }
    }

    private void validateEmailRecipient(String email) {
        if (!email.contains("@")) {
            logger.warn("Email recipient appears invalid: {}", maskRecipientForLogging(email));
        }
    }

    private String maskRecipientForLogging(String recipient) {
        int minLength = recipientVisibleChars * 2;
        if (recipient == null || recipient.length() <= minLength) {
            return "***";
        }
        return recipient.substring(0, recipientVisibleChars) + "***" + recipient.substring(recipient.length() - recipientVisibleChars);
    }
}
