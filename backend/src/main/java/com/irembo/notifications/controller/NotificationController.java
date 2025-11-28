package com.irembo.notifications.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    /**
     * Send a notification (stub implementation).
     * This endpoint is protected by the RateLimiterFilter.
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> sendNotification(
            @RequestHeader(value = "X-API-KEY", required = false) String apiKey,
            @RequestParam(required = false) String channel,
            @RequestBody(required = false) Map<String, Object> payload) {

        // Stub implementation - just return success
        Map<String, Object> response = Map.of(
                "status", "accepted",
                "message", "Notification queued for delivery",
                "channel", channel != null ? channel : "GENERAL",
                "timestamp", Instant.now().toString()
        );

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    /**
     * Get notification status (stub implementation).
     */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getNotificationStatus(@PathVariable String id) {
        Map<String, Object> response = Map.of(
                "id", id,
                "status", "delivered",
                "timestamp", Instant.now().toString()
        );

        return ResponseEntity.ok(response);
    }
}
