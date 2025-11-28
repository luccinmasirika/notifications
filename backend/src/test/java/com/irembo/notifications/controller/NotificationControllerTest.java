package com.irembo.notifications.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.irembo.notifications.model.dto.NotificationRequest;
import com.irembo.notifications.model.dto.NotificationResponse;
import com.irembo.notifications.model.enums.NotificationChannel;
import com.irembo.notifications.service.NotificationService;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(NotificationController.class)
@Disabled("Requires PostgreSQL and Redis - run with Docker: docker compose up -d postgres redis")
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private NotificationService notificationService;

    @Test
    @DisplayName("Should return 202 Accepted for valid SMS notification request")
    void shouldAcceptValidSmsRequest() throws Exception {
        // Given
        NotificationRequest request = new NotificationRequest(
                NotificationChannel.SMS,
                "+250700000001",
                "Test message"
        );

        NotificationResponse expectedResponse = NotificationResponse.accepted(
                NotificationChannel.SMS,
                "+250700000001",
                "Test Client"
        );

        when(notificationService.sendNotification(any(), anyLong(), anyString()))
                .thenReturn(expectedResponse);

        // When & Then
        mockMvc.perform(post("/api/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .requestAttr("clientId", 1L)
                        .requestAttr("clientName", "Test Client"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("accepted"))
                .andExpect(jsonPath("$.channel").value("SMS"))
                .andExpect(jsonPath("$.recipient").value("+250700000001"));
    }

    @Test
    @DisplayName("Should return 202 Accepted for valid EMAIL notification request")
    void shouldAcceptValidEmailRequest() throws Exception {
        // Given
        NotificationRequest request = new NotificationRequest(
                NotificationChannel.EMAIL,
                "test@example.com",
                "Test email message"
        );

        NotificationResponse expectedResponse = NotificationResponse.accepted(
                NotificationChannel.EMAIL,
                "test@example.com",
                "Test Client"
        );

        when(notificationService.sendNotification(any(), anyLong(), anyString()))
                .thenReturn(expectedResponse);

        // When & Then
        mockMvc.perform(post("/api/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .requestAttr("clientId", 1L)
                        .requestAttr("clientName", "Test Client"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("accepted"))
                .andExpect(jsonPath("$.channel").value("EMAIL"))
                .andExpect(jsonPath("$.recipient").value("test@example.com"));
    }

    @Test
    @DisplayName("Should return 400 Bad Request when channel is missing")
    void shouldRejectMissingChannel() throws Exception {
        // Given
        String invalidRequest = """
                {
                    "to": "+250700000001",
                    "message": "Test message"
                }
                """;

        // When & Then
        mockMvc.perform(post("/api/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequest)
                        .requestAttr("clientId", 1L)
                        .requestAttr("clientName", "Test Client"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should return 400 Bad Request when recipient is missing")
    void shouldRejectMissingRecipient() throws Exception {
        // Given
        String invalidRequest = """
                {
                    "channel": "SMS",
                    "message": "Test message"
                }
                """;

        // When & Then
        mockMvc.perform(post("/api/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequest)
                        .requestAttr("clientId", 1L)
                        .requestAttr("clientName", "Test Client"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should return 400 Bad Request when message is missing")
    void shouldRejectMissingMessage() throws Exception {
        // Given
        String invalidRequest = """
                {
                    "channel": "SMS",
                    "to": "+250700000001"
                }
                """;

        // When & Then
        mockMvc.perform(post("/api/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequest)
                        .requestAttr("clientId", 1L)
                        .requestAttr("clientName", "Test Client"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should return 400 Bad Request when message exceeds 500 characters")
    void shouldRejectMessageTooLong() throws Exception {
        // Given
        String longMessage = "a".repeat(501); // 501 characters
        NotificationRequest request = new NotificationRequest(
                NotificationChannel.SMS,
                "+250700000001",
                longMessage
        );

        // When & Then
        mockMvc.perform(post("/api/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .requestAttr("clientId", 1L)
                        .requestAttr("clientName", "Test Client"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'message')]").exists());
    }

    @Test
    @DisplayName("Should return 400 Bad Request for invalid channel")
    void shouldRejectInvalidChannel() throws Exception {
        // Given
        String invalidRequest = """
                {
                    "channel": "INVALID",
                    "to": "+250700000001",
                    "message": "Test message"
                }
                """;

        // When & Then
        mockMvc.perform(post("/api/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequest)
                        .requestAttr("clientId", 1L)
                        .requestAttr("clientName", "Test Client"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should accept message with exactly 500 characters")
    void shouldAcceptMessageWith500Characters() throws Exception {
        // Given
        String maxLengthMessage = "a".repeat(500); // Exactly 500 characters
        NotificationRequest request = new NotificationRequest(
                NotificationChannel.SMS,
                "+250700000001",
                maxLengthMessage
        );

        NotificationResponse expectedResponse = NotificationResponse.accepted(
                NotificationChannel.SMS,
                "+250700000001",
                "Test Client"
        );

        when(notificationService.sendNotification(any(), anyLong(), anyString()))
                .thenReturn(expectedResponse);

        // When & Then
        mockMvc.perform(post("/api/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .requestAttr("clientId", 1L)
                        .requestAttr("clientName", "Test Client"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("accepted"));
    }

    @Test
    @DisplayName("Should return 400 Bad Request when recipient is empty string")
    void shouldRejectEmptyRecipient() throws Exception {
        // Given
        NotificationRequest request = new NotificationRequest(
                NotificationChannel.SMS,
                "",
                "Test message"
        );

        // When & Then
        mockMvc.perform(post("/api/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .requestAttr("clientId", 1L)
                        .requestAttr("clientName", "Test Client"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'to')]").exists());
    }

    @Test
    @DisplayName("Should return 400 Bad Request when message is empty string")
    void shouldRejectEmptyMessage() throws Exception {
        // Given
        NotificationRequest request = new NotificationRequest(
                NotificationChannel.SMS,
                "+250700000001",
                ""
        );

        // When & Then
        mockMvc.perform(post("/api/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .requestAttr("clientId", 1L)
                        .requestAttr("clientName", "Test Client"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'message')]").exists());
    }

    @Test
    @DisplayName("Should include timestamp in response")
    void shouldIncludeTimestampInResponse() throws Exception {
        // Given
        NotificationRequest request = new NotificationRequest(
                NotificationChannel.SMS,
                "+250700000001",
                "Test message"
        );

        NotificationResponse expectedResponse = NotificationResponse.accepted(
                NotificationChannel.SMS,
                "+250700000001",
                "Test Client"
        );

        when(notificationService.sendNotification(any(), anyLong(), anyString()))
                .thenReturn(expectedResponse);

        // When & Then
        mockMvc.perform(post("/api/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .requestAttr("clientId", 1L)
                        .requestAttr("clientName", "Test Client"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.timestamp").exists());
    }
}
