package com.irembo.notifications.infra.db.entity;

import com.irembo.notifications.model.enums.NotificationChannel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NotificationEntityTest {

    @Test
    void prePersistSetsDefaultsWhenMissing() {
        Notification notification = new Notification();
        notification.setChannel(NotificationChannel.SMS);
        notification.setRecipient("+250700000000");
        notification.setMessage("hello");

        notification.onCreate();

        assertNotNull(notification.getCreatedAt());
        assertEquals("PENDING", notification.getStatus());
    }

    @Test
    void prePersistDoesNotOverrideExistingValues() {
        Notification notification = new Notification();
        notification.setStatus("CUSTOM");
        notification.setCreatedAt(java.time.LocalDateTime.now().minusHours(1));

        var originalCreatedAt = notification.getCreatedAt();

        notification.onCreate();

        assertEquals("CUSTOM", notification.getStatus());
        assertEquals(originalCreatedAt, notification.getCreatedAt());
    }
}
