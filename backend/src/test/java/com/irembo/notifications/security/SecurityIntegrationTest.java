package com.irembo.notifications.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.irembo.notifications.model.dto.NotificationRequest;
import com.irembo.notifications.model.enums.NotificationChannel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.format_sql=false",
        "spring.jpa.properties.hibernate.jdbc.lob.non_contextual_creation=true",
        "spring.flyway.enabled=false",
        "spring.data.redis.host=localhost",
        "spring.data.redis.port=6379",
        "spring.rabbitmq.host=localhost",
        "spring.rabbitmq.port=5672",
        "management.health.rabbit.enabled=false"
})
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Should return 401 when API key header is missing")
    void shouldReturn401WhenApiKeyMissing() throws Exception {
        NotificationRequest request = new NotificationRequest(
                NotificationChannel.SMS,
                "+1234567890",
                "Test message"
        );

        mockMvc.perform(post("/api/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Missing X-API-KEY header"));
    }

    @Test
    @DisplayName("Should return 401 when API key is empty")
    void shouldReturn401WhenApiKeyEmpty() throws Exception {
        NotificationRequest request = new NotificationRequest(
                NotificationChannel.SMS,
                "+1234567890",
                "Test message"
        );

        mockMvc.perform(post("/api/notifications")
                        .header("X-API-KEY", "")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Missing X-API-KEY header"));
    }

    @Test
    @DisplayName("Should return 401 when API key is invalid")
    void shouldReturn401WhenApiKeyInvalid() throws Exception {
        NotificationRequest request = new NotificationRequest(
                NotificationChannel.SMS,
                "+1234567890",
                "Test message"
        );

        mockMvc.perform(post("/api/notifications")
                        .header("X-API-KEY", "invalid-api-key-12345")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Invalid API key"));
    }

    @Test
    @DisplayName("Should return 401 when accessing admin endpoint without credentials")
    void shouldReturn401ForAdminEndpointWithoutAuth() throws Exception {
        mockMvc.perform(get("/admin/clients"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Should allow access to health endpoint without authentication")
    void shouldAllowHealthEndpointWithoutAuth() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Should allow access to actuator health endpoint without authentication")
    void shouldAllowActuatorHealthWithoutAuth() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").exists());
    }

    @Test
    @DisplayName("Should return 401 for non-existent API key")
    void shouldReturn401ForNonExistentApiKey() throws Exception {
        NotificationRequest request = new NotificationRequest(
                NotificationChannel.EMAIL,
                "test@example.com",
                "Test email message"
        );

        mockMvc.perform(post("/api/notifications")
                        .header("X-API-KEY", "non-existent-key-that-does-not-exist-in-db")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Invalid API key"));
    }

    @Test
    @DisplayName("Should mask API key in error responses")
    void shouldMaskApiKeyInErrorResponses() throws Exception {
        NotificationRequest request = new NotificationRequest(
                NotificationChannel.SMS,
                "+1234567890",
                "Test message"
        );

        String apiKey = "test-invalid-key-123";

        mockMvc.perform(post("/api/notifications")
                        .header("X-API-KEY", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid API key"));
    }

    @Test
    @DisplayName("Should protect all /api/* endpoints with API key")
    void shouldProtectApiEndpointsWithApiKey() throws Exception {
        mockMvc.perform(post("/api/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Should enforce API key header name (case sensitive)")
    void shouldEnforceCorrectHeaderName() throws Exception {
        NotificationRequest request = new NotificationRequest(
                NotificationChannel.SMS,
                "+1234567890",
                "Test message"
        );

        mockMvc.perform(post("/api/notifications")
                        .header("x-api-key", "test-api-key-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }
}
