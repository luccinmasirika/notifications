package com.irembo.notifications.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.irembo.notifications.infra.db.entity.Client;
import com.irembo.notifications.infra.db.entity.ClientLimit;
import com.irembo.notifications.infra.db.repository.ClientLimitRepository;
import com.irembo.notifications.infra.db.repository.ClientRepository;
import com.irembo.notifications.model.dto.NotificationRequest;
import com.irembo.notifications.model.enums.NotificationChannel;
import com.irembo.notifications.service.ApiKeyHashService;
import com.irembo.notifications.service.ApiKeyValidationService;
import com.irembo.notifications.service.CryptoService;
import com.irembo.notifications.service.HMACSignerService;
import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
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

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@org.springframework.test.context.TestPropertySource(properties = {
        "spring.flyway.enabled=false"
})
class RateLimiterIntegrationTest {

    static {
        // Configure Docker host for Colima
        String dockerHost = System.getProperty("docker.host");
        if (dockerHost == null || dockerHost.isEmpty()) {
            String colimaSocket = System.getProperty("user.home") + "/.colima/default/docker.sock";
            if (new java.io.File(colimaSocket).exists()) {
                System.setProperty("docker.host", "unix://" + colimaSocket);
            }
        }
    }

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test")
            .waitingFor(org.testcontainers.containers.wait.strategy.Wait.forLogMessage(".*database system is ready to accept connections.*", 2))
            .withStartupTimeout(java.time.Duration.ofSeconds(120))
            .withReuse(true);

    @Container
    static RedisContainer redis = new RedisContainer(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)
            .waitingFor(org.testcontainers.containers.wait.strategy.Wait.forListeningPort())
            .withStartupTimeout(java.time.Duration.ofSeconds(60))
            .withReuse(true);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379).toString());
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private ClientLimitRepository clientLimitRepository;

    @Autowired
    private ApiKeyHashService apiKeyHashService;

    @Autowired
    private ApiKeyValidationService apiKeyValidationService;

    @Autowired
    private CryptoService cryptoService;

    @Autowired
    private HMACSignerService hmacSignerService;

    @MockBean
    private RabbitTemplate rabbitTemplate;

    private Client testClient;
    private String testApiKey = "integration-test-key-12345";
    private String testApiSecret = "integration-test-secret-1234567890123456789012345678901234567890123456789012345678901234";

    @BeforeEach
    void setUp() {
        clientLimitRepository.deleteAll();
        clientRepository.deleteAll();

        testClient = new Client();
        testClient.setName("Integration Test Client");
        testClient.setActive(true);
        testClient.setPriority(1);
        testClient.setAuthMethod("HMAC");
        testClient.setStatus("ACTIVE");
        
        // Set up API key properly with index and salt
        String apiKeyIndex = apiKeyHashService.calculateApiKeyIndex(testApiKey);
        String[] hashAndSalt = apiKeyValidationService.hashApiKeyForStorage(testApiKey, null);
        String hashedApiKey = hashAndSalt[0];
        String clientSalt = hashAndSalt[1];
        
        testClient.setApiKeyIndex(apiKeyIndex);
        testClient.setApiKeyHash(hashedApiKey);
        testClient.setClientSalt(clientSalt);
        
        // Set encrypted API secret for HMAC signing
        String encryptedSecret = cryptoService.encrypt(testApiSecret);
        testClient.setApiSecretEncrypted(encryptedSecret);
        
        testClient = clientRepository.save(testClient);

        ClientLimit limit = new ClientLimit();
        limit.setClientId(testClient.getId());
        limit.setWindowSizeSeconds(10);
        limit.setMaxRequestsPerWindow(5);
        limit.setMonthlyQuota(100);
        limit.setSoftThrottleThreshold(0.80);
        limit.setHardRejectThreshold(1.00);
        clientLimitRepository.save(limit);
    }

    private org.springframework.test.web.servlet.ResultActions performNotificationRequest(
            NotificationRequest request) throws Exception {
        long timestamp = System.currentTimeMillis();
        String requestBody = objectMapper.writeValueAsString(request);
        String signature = hmacSignerService.generateSignature(testApiSecret, timestamp, "POST", "/api/notifications", requestBody);
        
        return mockMvc.perform(post("/api/notifications")
                        .header("X-API-KEY", testApiKey)
                        .header("X-TIMESTAMP", String.valueOf(timestamp))
                        .header("X-SIGNATURE", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody));
    }

    @Test
    @DisplayName("Should allow requests below threshold")
    void shouldAllowRequestsBelowThreshold() throws Exception {
        NotificationRequest request = new NotificationRequest(
                NotificationChannel.SMS,
                "+1234567890",
                "Test message"
        );

        performNotificationRequest(request)
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

        performNotificationRequest(request)
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

        // Send 3 requests (should all be accepted, at 60% usage)
        for (int i = 0; i < 3; i++) {
            performNotificationRequest(request)
                    .andExpect(status().isAccepted());
        }

        // 4th request should still be accepted (at 80%, soft throttle threshold)
        performNotificationRequest(request)
                .andExpect(status().isAccepted());
    }

    @Test
    @DisplayName("Should hard reject when limit exceeded (100%)")
    void shouldHardRejectWhenLimitExceeded() throws Exception {
        NotificationRequest request = new NotificationRequest(
                NotificationChannel.SMS,
                "+1234567890",
                "Test message"
        );

        // Send 4 requests (should all be accepted)
        for (int i = 0; i < 4; i++) {
            performNotificationRequest(request)
                    .andExpect(status().isAccepted());
        }

        // 5th request should be rejected (hits 100% limit)
        performNotificationRequest(request)
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

        // Send 4 requests (should all be accepted)
        for (int i = 0; i < 4; i++) {
            performNotificationRequest(request)
                    .andExpect(status().isAccepted());
        }

        // 5th request should be rejected with Retry-After header
        performNotificationRequest(request)
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

        performNotificationRequest(request)
                .andExpect(status().isAccepted())
                .andExpect(header().string("X-RateLimit-Limit", "5"))
                .andExpect(header().exists("X-RateLimit-Remaining"));

        performNotificationRequest(request)
                .andExpect(status().isAccepted())
                .andExpect(header().exists("X-RateLimit-Remaining"));
    }

    @Test
    @DisplayName("Should reject inactive client")
    void shouldRejectInactiveClient() throws Exception {
        testClient.setActive(false);
        clientRepository.save(testClient);

        NotificationRequest request = new NotificationRequest(
                NotificationChannel.SMS,
                "+1234567890",
                "Test message"
        );

        performNotificationRequest(request)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Client account is inactive"));
    }

    @Test
    @DisplayName("Should handle concurrent requests correctly")
    void shouldHandleConcurrentRequests() throws Exception {
        NotificationRequest request = new NotificationRequest(
                NotificationChannel.SMS,
                "+1234567890",
                "Test message"
        );

        for (int i = 0; i < 3; i++) {
            performNotificationRequest(request)
                    .andExpect(status().isAccepted());
        }

        performNotificationRequest(request)
                .andExpect(status().isAccepted())
                .andExpect(header().exists("X-RateLimit-Remaining"));
    }

    @Test
    @DisplayName("Should validate notification request fields")
    void shouldValidateNotificationRequest() throws Exception {
        NotificationRequest invalidRequest = new NotificationRequest(
                null,
                "",
                ""
        );

        long timestamp = System.currentTimeMillis();
        String signature = hmacSignerService.generateSignature(testApiSecret, timestamp, "POST", "/api/notifications", objectMapper.writeValueAsString(invalidRequest));
        
        mockMvc.perform(post("/api/notifications")
                        .header("X-API-KEY", testApiKey)
                        .header("X-TIMESTAMP", String.valueOf(timestamp))
                        .header("X-SIGNATURE", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }
}
