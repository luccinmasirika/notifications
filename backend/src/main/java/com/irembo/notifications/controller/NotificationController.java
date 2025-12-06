package com.irembo.notifications.controller;

import com.irembo.notifications.model.dto.NotificationRequest;
import com.irembo.notifications.model.dto.NotificationResponse;
import com.irembo.notifications.service.NotificationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @PostMapping
    public ResponseEntity<NotificationResponse> sendNotification(
            @Valid @RequestBody NotificationRequest request,
            HttpServletRequest httpRequest) {

        String clientName = (String) httpRequest.getAttribute("clientName");
        Long clientId = (Long) httpRequest.getAttribute("clientId");

        NotificationResponse response = notificationService.queueNotification(
                request,
                clientId,
                clientName
        );

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }
}
