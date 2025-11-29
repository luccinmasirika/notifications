package com.irembo.notifications.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.irembo.notifications.infra.db.entity.Client;
import com.irembo.notifications.infra.db.entity.ClientLimit;
import com.irembo.notifications.infra.db.repository.ClientLimitRepository;
import com.irembo.notifications.infra.db.repository.ClientRepository;
import com.irembo.notifications.model.dto.NotificationRequest;
import com.irembo.notifications.model.enums.NotificationChannel;
import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for rate limiting with real Redis and PostgreSQL using Testcontainers.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class RateLimiterIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @Container
    static RedisContainer redis = new RedisContainer(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379).toString());
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private ClientLimitRepository clientLimitRepository;

    private Client testClient;
    private String testApiKey = "integration-test-key-12345";

    @BeforeEach
    void setUp() {
        // Clean up
        clientLimitRepository.deleteAll();
        clientRepository.deleteAll();

        // Create test client
        testClient = new Client();
        testClient.setApiKey(testApiKey);
        testClient.setName("Integration Test Client");
        testClient.setActive(true);
        testClient.setPriority(1);
        testClient = clientRepository.save(testClient);

        // Create client limits
        ClientLimit limit = new ClientLimit();
        limit.setClientId(testClient.getId());
        limit.setWindowSizeSeconds(10);
        limit.setMaxRequestsPerWindow(5); // Low limit for testing
        limit.setMonthlyQuota(100);
        clientLimitRepository.save(limit);
    }

    @Test
    @DisplayName("Should allow requests below threshold")
    void shouldAllowRequestsBelowThreshold() throws Exception {
        NotificationRequest request = new NotificationRequest(
                NotificationChannel.SMS,
                "+1234567890",
                "Test message"
        );

        // First request should be allowed
        mockMvc.perform(post("/api/notifications")
                        .header("X-API-KEY", testApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("accepted"))
                .andExpect(header().exists("X-RateLimit-Limit"))
                .andExpect(header().exists("X-RateLimit-Remaining"))
                .andExpect(header().exists("X-RateLimit-Reset"));
    }

    @Test
    @DisplayName("Should return rate limit headers in all responses")
    void shouldReturnRateLimitHeaders() throws Exception {
        NotificationRequest request = new NotificationRequest(
                NotificationChannel.EMAIL,
                "test@example.com",
                "Test email"
        );

        mockMvc.perform(post("/api/notifications")
                        .header("X-API-KEY", testApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(header().string("X-RateLimit-Limit", "5"))
                .andExpect(header().exists("X-RateLimit-Remaining"))
                .andExpect(header().exists("X-RateLimit-Reset"));
    }

    @Test
    @DisplayName("Should soft throttle when approaching limit (80%)")
    void shouldSoftThrottleNearLimit() throws Exception {
        NotificationRequest request = new NotificationRequest(
                NotificationChannel.SMS,
                "+1234567890",
                "Test message"
        );

        // Make 4 requests (80% of 5)
        for (int i = 0; i < 4; i++) {
            mockMvc.perform(post("/api/notifications")
                            .header("X-API-KEY", testApiKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isAccepted());
        }

        // 5th request should be soft throttled (but still accepted)
        mockMvc.perform(post("/api/notifications")
                        .header("X-API-KEY", testApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(header().string("X-RateLimit-Remaining", "0"));
    }

    @Test
    @DisplayName("Should hard reject when limit exceeded (100%)")
    void shouldHardRejectWhenLimitExceeded() throws Exception {
        NotificationRequest request = new NotificationRequest(
                NotificationChannel.SMS,
                "+1234567890",
                "Test message"
        );

        // Exhaust the limit (5 requests)
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/notifications")
                            .header("X-API-KEY", testApiKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isAccepted());
        }

        // 6th request should be rejected
        mockMvc.perform(post("/api/notifications")
                        .header("X-API-KEY", testApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("Rate limit exceeded"))
                .andExpect(header().exists("Retry-After"))
                .andExpect(header().string("X-RateLimit-Remaining", "0"));
    }

    @Test
    @DisplayName("Should include Retry-After header in 429 response")
    void shouldIncludeRetryAfterHeader() throws Exception {
        NotificationRequest request = new NotificationRequest(
                NotificationChannel.SMS,
                "+1234567890",
                "Test message"
        );

        // Exhaust limit
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/notifications")
                            .header("X-API-KEY", testApiKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isAccepted());
        }

        // Next request should have Retry-After
        mockMvc.perform(post("/api/notifications")
                        .header("X-API-KEY", testApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(header().string("Retry-After", not(emptyString())));
    }

    @Test
    @DisplayName("Should decrement remaining count with each request")
    void shouldDecrementRemainingCount() throws Exception {
        NotificationRequest request = new NotificationRequest(
                NotificationChannel.SMS,
                "+1234567890",
                "Test message"
        );

        // First request
        mockMvc.perform(post("/api/notifications")
                        .header("X-API-KEY", testApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(header().string("X-RateLimit-Limit", "5"))
                .andExpect(header().exists("X-RateLimit-Remaining"));

        // Second request - should have less remaining
        mockMvc.perform(post("/api/notifications")
                        .header("X-API-KEY", testApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(header().exists("X-RateLimit-Remaining"));
    }

    @Test
    @DisplayName("Should reject inactive client")
    void shouldRejectInactiveClient() throws Exception {
        // Deactivate client
        testClient.setActive(false);
        clientRepository.save(testClient);

        NotificationRequest request = new NotificationRequest(
                NotificationChannel.SMS,
                "+1234567890",
                "Test message"
        );

        mockMvc.perform(post("/api/notifications")
                        .header("X-API-KEY", testApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("Rate limit exceeded"));
    }

    @Test
    @DisplayName("Should handle concurrent requests correctly")
    void shouldHandleConcurrentRequests() throws Exception {
        NotificationRequest request = new NotificationRequest(
                NotificationChannel.SMS,
                "+1234567890",
                "Test message"
        );

        // Make 3 concurrent requests (should all succeed)
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/notifications")
                            .header("X-API-KEY", testApiKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isAccepted());
        }

        // Verify counters are correctly incremented
        mockMvc.perform(post("/api/notifications")
                        .header("X-API-KEY", testApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(header().exists("X-RateLimit-Remaining"));
    }

    @Test
    @DisplayName("Should validate notification request fields")
    void shouldValidateNotificationRequest() throws Exception {
        // Invalid request - missing required fields
        NotificationRequest invalidRequest = new NotificationRequest(
                null,
                "",
                ""
        );

        mockMvc.perform(post("/api/notifications")
                        .header("X-API-KEY", testApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }
}
