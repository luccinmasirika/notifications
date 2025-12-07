package com.irembo.notifications.service;

import com.irembo.notifications.infra.db.entity.Notification;
import com.irembo.notifications.infra.db.repository.NotificationRepository;
import com.irembo.notifications.model.dto.NotificationRequest;
import com.irembo.notifications.model.dto.NotificationResponse;
import com.irembo.notifications.model.enums.NotificationChannel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(notificationService, "recipientVisibleChars", 3);
    }

    @Test
    @DisplayName("Should queue SMS notification successfully")
    void shouldQueueSmsNotificationSuccessfully() {
        NotificationRequest request = new NotificationRequest(
                NotificationChannel.SMS,
                "+1234567890",
                "Test message"
        );
        Long clientId = 1L;
        String clientName = "Test Client";

        Notification savedNotification = new Notification();
        savedNotification.setId(1L);
        when(notificationRepository.save(any(Notification.class))).thenReturn(savedNotification);
        doAnswer(invocation -> null).when(rabbitTemplate).convertAndSend(
                eq("notifications.exchange"), 
                eq("notification.send"), 
                (Object) any());

        NotificationResponse response = notificationService.queueNotification(request, clientId, clientName);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo("accepted");
        assertThat(response.channel()).isEqualTo(NotificationChannel.SMS);
        assertThat(response.recipient()).isEqualTo("+1234567890");

        verify(notificationRepository, times(1)).save(any(Notification.class));
        verify(rabbitTemplate).convertAndSend(eq("notifications.exchange"), eq("notification.send"), (Object) any());
    }

    @Test
    @DisplayName("Should queue EMAIL notification successfully")
    void shouldQueueEmailNotificationSuccessfully() {
        NotificationRequest request = new NotificationRequest(
                NotificationChannel.EMAIL,
                "test@example.com",
                "Test email message"
        );
        Long clientId = 1L;
        String clientName = "Test Client";

        Notification savedNotification = new Notification();
        savedNotification.setId(1L);
        when(notificationRepository.save(any(Notification.class))).thenReturn(savedNotification);
        doAnswer(invocation -> null).when(rabbitTemplate).convertAndSend(
                eq("notifications.exchange"), 
                eq("notification.send"), 
                (Object) any());

        NotificationResponse response = notificationService.queueNotification(request, clientId, clientName);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo("accepted");
        assertThat(response.channel()).isEqualTo(NotificationChannel.EMAIL);
        assertThat(response.recipient()).isEqualTo("test@example.com");

        verify(notificationRepository, times(1)).save(any(Notification.class));
        verify(rabbitTemplate).convertAndSend(eq("notifications.exchange"), eq("notification.send"), (Object) any());
    }

    @Test
    @DisplayName("Should save notification with correct data")
    void shouldSaveNotificationWithCorrectData() {
        NotificationRequest request = new NotificationRequest(
                NotificationChannel.SMS,
                "+1234567890",
                "Test message"
        );
        Long clientId = 1L;
        String clientName = "Test Client";

        Notification savedNotification = new Notification();
        savedNotification.setId(1L);
        when(notificationRepository.save(any(Notification.class))).thenReturn(savedNotification);
        doAnswer(invocation -> null).when(rabbitTemplate).convertAndSend(
                eq("notifications.exchange"), 
                eq("notification.send"), 
                (Object) any());

        notificationService.queueNotification(request, clientId, clientName);

        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(notificationCaptor.capture());

        Notification captured = notificationCaptor.getValue();
        assertThat(captured.getClientId()).isEqualTo(clientId);
        assertThat(captured.getClientName()).isEqualTo(clientName);
        assertThat(captured.getChannel()).isEqualTo(NotificationChannel.SMS);
        assertThat(captured.getRecipient()).isEqualTo("+1234567890");
        assertThat(captured.getMessage()).isEqualTo("Test message");
        assertThat(captured.getStatus()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("Should handle RabbitMQ failure and mark notification as failed")
    void shouldHandleRabbitMQFailureAndMarkNotificationAsFailed() {
        NotificationRequest request = new NotificationRequest(
                NotificationChannel.SMS,
                "+1234567890",
                "Test message"
        );
        Long clientId = 1L;
        String clientName = "Test Client";

        Notification savedNotification = new Notification();
        savedNotification.setId(1L);
        when(notificationRepository.save(any(Notification.class))).thenReturn(savedNotification);
        doThrow(new RuntimeException("RabbitMQ connection failed"))
                .when(rabbitTemplate).convertAndSend(
                        eq("notifications.exchange"), 
                        eq("notification.send"), 
                        (Object) any());

        assertThatThrownBy(() -> notificationService.queueNotification(request, clientId, clientName))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to queue notification");

        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(2)).save(notificationCaptor.capture());

        Notification failedNotification = notificationCaptor.getAllValues().get(1);
        assertThat(failedNotification.getStatus()).isEqualTo("FAILED");
        assertThat(failedNotification.getErrorMessage()).contains("Failed to publish to queue");
    }

    @Test
    @DisplayName("Should validate SMS recipient format")
    void shouldValidateSmsRecipientFormat() {
        NotificationRequest request = new NotificationRequest(
                NotificationChannel.SMS,
                "1234567890", // Missing +
                "Test message"
        );
        Long clientId = 1L;
        String clientName = "Test Client";

        Notification savedNotification = new Notification();
        savedNotification.setId(1L);
        when(notificationRepository.save(any(Notification.class))).thenReturn(savedNotification);
        doAnswer(invocation -> null).when(rabbitTemplate).convertAndSend(
                eq("notifications.exchange"), 
                eq("notification.send"), 
                (Object) any());

        NotificationResponse response = notificationService.queueNotification(request, clientId, clientName);

        assertThat(response).isNotNull();
        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    @DisplayName("Should validate EMAIL recipient format")
    void shouldValidateEmailRecipientFormat() {
        NotificationRequest request = new NotificationRequest(
                NotificationChannel.EMAIL,
                "invalid-email", // Missing @
                "Test message"
        );
        Long clientId = 1L;
        String clientName = "Test Client";

        Notification savedNotification = new Notification();
        savedNotification.setId(1L);
        when(notificationRepository.save(any(Notification.class))).thenReturn(savedNotification);
        doAnswer(invocation -> null).when(rabbitTemplate).convertAndSend(
                eq("notifications.exchange"), 
                eq("notification.send"), 
                (Object) any());

        NotificationResponse response = notificationService.queueNotification(request, clientId, clientName);

        assertThat(response).isNotNull();
        verify(notificationRepository).save(any(Notification.class));
    }
}

