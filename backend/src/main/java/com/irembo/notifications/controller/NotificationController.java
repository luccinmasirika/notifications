package com.irembo.notifications.controller;

import com.irembo.notifications.model.dto.NotificationRequest;
import com.irembo.notifications.model.dto.NotificationResponse;
import com.irembo.notifications.service.NotificationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Notification Controller.
 * Handles notification requests.
 *
 * Protected by:
 * - APIKeyAuthFilter (validates X-API-KEY)
 * - RateLimiterFilter (enforces rate limits)
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /**
     * Send a notification.
     *
     * Request body must contain:
     * - channel: "SMS" or "EMAIL"
     * - to: recipient (e.g., phone number or email)
     * - message: notification message (max 500 chars)
     *
     * Returns HTTP 202 Accepted if notification is queued.
     * Rate limit headers are added by RateLimiterFilter.
     */
    @PostMapping
    public ResponseEntity<NotificationResponse> sendNotification(
            @Valid @RequestBody NotificationRequest request,
            HttpServletRequest httpRequest) {

        // Get authenticated client info from request attributes
        // (set by APIKeyAuthFilter)
        String clientName = (String) httpRequest.getAttribute("clientName");
        Long clientId = (Long) httpRequest.getAttribute("clientId");

        // Queue notification for asynchronous processing
        NotificationResponse response = notificationService.queueNotification(
                request,
                clientId,
                clientName
        );

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }
}
