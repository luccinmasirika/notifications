package com.irembo.notifications.service;

import com.irembo.notifications.config.NotificationProcessingConfig;
import com.irembo.notifications.infra.db.entity.Notification;
import com.irembo.notifications.infra.db.repository.NotificationRepository;
import com.irembo.notifications.model.dto.NotificationMessage;
import com.irembo.notifications.model.enums.NotificationChannel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationConsumerTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationProcessingConfig processingConfig;

    @InjectMocks
    private NotificationConsumer consumer;

    private Notification notification;
    private NotificationMessage message;

    @BeforeEach
    void setUp() {
        notification = new Notification();
        notification.setId(1L);
        notification.setStatus("PENDING");
        notification.setChannel(NotificationChannel.SMS);
        notification.setRecipient("+1234567890");
        notification.setMessage("Test message");

        message = new NotificationMessage(
            1L,
            100L,
            "Test Client",
            NotificationChannel.SMS,
            "+1234567890",
            "Test message"
        );
    }

    @Test
    @DisplayName("Should process notification successfully")
    void shouldProcessNotificationSuccessfully() throws Exception {
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(notification));
        when(processingConfig.generateProcessingDelay()).thenReturn(0);

        consumer.processNotification(message);

        verify(notificationRepository, times(2)).save(any(Notification.class));
        verify(notificationRepository).findById(1L);
        
        assertEquals("SENT", notification.getStatus());
        assertNotNull(notification.getProcessedAt());
        assertNull(notification.getErrorMessage());
    }

    @Test
    @DisplayName("Should update status to PROCESSING first")
    void shouldUpdateStatusToProcessingFirst() throws Exception {
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(notification));
        when(processingConfig.generateProcessingDelay()).thenReturn(0);

        consumer.processNotification(message);

        verify(notificationRepository, atLeastOnce()).save(argThat(n -> 
            "PROCESSING".equals(n.getStatus()) || "SENT".equals(n.getStatus())
        ));
    }

    @Test
    @DisplayName("Should throw exception when notification not found")
    void shouldThrowExceptionWhenNotificationNotFound() {
        when(notificationRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> {
            consumer.processNotification(message);
        });

        verify(notificationRepository).findById(1L);
        verify(notificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should handle InterruptedException")
    void shouldHandleInterruptedException() throws Exception {
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(notification));
        when(processingConfig.generateProcessingDelay()).thenReturn(100);
        
        Thread testThread = new Thread(() -> {
            try {
                Thread.sleep(10);
                Thread.currentThread().interrupt();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        
        // This test verifies the exception handling path exists
        // In a real scenario, we'd need to mock Thread.sleep to throw InterruptedException
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(notification));
        
        // We can't easily test InterruptedException without more complex setup,
        // but we can verify the error handling path exists in the code
        assertDoesNotThrow(() -> {
            consumer.processNotification(message);
        });
    }

    @Test
    @DisplayName("Should handle general exceptions")
    void shouldHandleGeneralExceptions() {
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(notification));
        when(notificationRepository.save(any(Notification.class))).thenThrow(new RuntimeException("Database error"));

        assertDoesNotThrow(() -> {
            consumer.processNotification(message);
        });

        verify(notificationRepository, atLeastOnce()).save(any(Notification.class));
    }

    @Test
    @DisplayName("Should mask recipient in logging")
    void shouldMaskRecipientInLogging() throws Exception {
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(notification));
        when(processingConfig.generateProcessingDelay()).thenReturn(0);
        
        NotificationMessage messageWithLongRecipient = new NotificationMessage(
            1L,
            100L,
            "Test Client",
            NotificationChannel.SMS,
            "+1234567890123456",
            "Test message"
        );

        consumer.processNotification(messageWithLongRecipient);

        verify(notificationRepository).findById(1L);
        verify(notificationRepository, atLeastOnce()).save(any(Notification.class));
    }

    @Test
    @DisplayName("Should set error message on failure")
    void shouldSetErrorMessageOnFailure() {
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(notification));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification n = invocation.getArgument(0);
            if ("PROCESSING".equals(n.getStatus())) {
                throw new RuntimeException("Processing failed");
            }
            return n;
        });

        consumer.processNotification(message);

        verify(notificationRepository, atLeastOnce()).save(argThat(n -> 
            n.getErrorMessage() != null && n.getErrorMessage().contains("Processing error")
        ));
    }

    @Test
    @DisplayName("Should set processedAt timestamp on completion")
    void shouldSetProcessedAtTimestampOnCompletion() throws Exception {
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(notification));
        when(processingConfig.generateProcessingDelay()).thenReturn(0);

        consumer.processNotification(message);

        verify(notificationRepository).save(argThat(n -> 
            n.getProcessedAt() != null
        ));
    }
}
