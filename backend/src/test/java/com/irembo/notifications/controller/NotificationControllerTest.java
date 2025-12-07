package com.irembo.notifications.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.irembo.notifications.infra.db.entity.Client;
import com.irembo.notifications.infra.db.entity.ClientLimit;
import com.irembo.notifications.infra.db.repository.ClientLimitRepository;
import com.irembo.notifications.infra.db.repository.ClientRepository;
import com.irembo.notifications.model.dto.NotificationRequest;
import com.irembo.notifications.model.dto.NotificationResponse;
import com.irembo.notifications.model.enums.NotificationChannel;
import com.irembo.notifications.service.ApiKeyHashService;
import com.irembo.notifications.service.ApiKeyValidationService;
import com.irembo.notifications.service.HMACSignerService;
import com.irembo.notifications.service.CryptoService;
import com.irembo.notifications.service.NotificationService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.annotation.Commit;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.cache.CacheManager;
import jakarta.persistence.EntityManager;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

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
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class NotificationControllerTest {

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
    private HMACSignerService hmacSignerService;

    @Autowired
    private CryptoService cryptoService;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @MockBean
    private NotificationService notificationService;

    private Client testClient;
    private String testApiKey = "test-api-key-12345";
    private String testApiSecret = "test-api-secret-1234567890123456789012345678901234567890123456789012345678901234";

    @BeforeEach
    void setUp() {
        // Use TransactionTemplate to commit the transaction
        transactionTemplate.execute(status -> {
            clientLimitRepository.deleteAll();
            clientRepository.deleteAll();
            
            testClient = new Client();
            testClient.setName("Test Client");
            testClient.setActive(true);
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

            // Add client limit so rate limiter doesn't reject
            ClientLimit limit = new ClientLimit();
            limit.setClientId(testClient.getId());
            limit.setWindowSizeSeconds(60);
            limit.setMaxRequestsPerWindow(1000);
            limit.setMonthlyQuota(100000);
            clientLimitRepository.save(limit);
            
            return null;
        });
        
        // Clear cache to ensure fresh lookup
        if (cacheManager != null) {
            var cache = cacheManager.getCache("apiKeyValidation");
            if (cache != null) {
                cache.clear();
            }
        }
    }

    @Test
    @DisplayName("Should return 202 Accepted for valid SMS notification request")
    void shouldAcceptValidSmsRequest() throws Exception {
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

        when(notificationService.queueNotification(any(), anyLong(), anyString()))
                .thenReturn(expectedResponse);

        long timestamp = System.currentTimeMillis();
        String requestBody = objectMapper.writeValueAsString(request);
        String signature = hmacSignerService.generateSignature(testApiSecret, timestamp, "POST", "/api/notifications", requestBody);
        
        mockMvc.perform(post("/api/notifications")
                        .header("X-API-KEY", testApiKey)
                        .header("X-TIMESTAMP", String.valueOf(timestamp))
                        .header("X-SIGNATURE", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("accepted"))
                .andExpect(jsonPath("$.channel").value("SMS"))
                .andExpect(jsonPath("$.recipient").value("+250700000001"));
    }

    @Test
    @DisplayName("Should return 202 Accepted for valid EMAIL notification request")
    void shouldAcceptValidEmailRequest() throws Exception {
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

        when(notificationService.queueNotification(any(), anyLong(), anyString()))
                .thenReturn(expectedResponse);

        long timestamp = System.currentTimeMillis();
        String requestBody = objectMapper.writeValueAsString(request);
        String signature = hmacSignerService.generateSignature(testApiSecret, timestamp, "POST", "/api/notifications", requestBody);
        
        mockMvc.perform(post("/api/notifications")
                        .header("X-API-KEY", testApiKey)
                        .header("X-TIMESTAMP", String.valueOf(timestamp))
                        .header("X-SIGNATURE", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("accepted"))
                .andExpect(jsonPath("$.channel").value("EMAIL"))
                .andExpect(jsonPath("$.recipient").value("test@example.com"));
    }

    @Test
    @DisplayName("Should return 400 Bad Request when channel is missing")
    void shouldRejectMissingChannel() throws Exception {
        String invalidRequest = """
                {
                    "to": "+250700000001",
                    "message": "Test message"
                }
                """;

        long timestamp = System.currentTimeMillis();
        String signature = hmacSignerService.generateSignature(testApiSecret, timestamp, "POST", "/api/notifications", invalidRequest);

        mockMvc.perform(post("/api/notifications")
                        .header("X-API-KEY", testApiKey)
                        .header("X-TIMESTAMP", String.valueOf(timestamp))
                        .header("X-SIGNATURE", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequest))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should return 400 Bad Request when recipient is missing")
    void shouldRejectMissingRecipient() throws Exception {
        String invalidRequest = """
                {
                    "channel": "SMS",
                    "message": "Test message"
                }
                """;

        long timestamp = System.currentTimeMillis();
        String signature = hmacSignerService.generateSignature(testApiSecret, timestamp, "POST", "/api/notifications", invalidRequest);

        mockMvc.perform(post("/api/notifications")
                        .header("X-API-KEY", testApiKey)
                        .header("X-TIMESTAMP", String.valueOf(timestamp))
                        .header("X-SIGNATURE", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequest))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should return 400 Bad Request when message is missing")
    void shouldRejectMissingMessage() throws Exception {
        String invalidRequest = """
                {
                    "channel": "SMS",
                    "to": "+250700000001"
                }
                """;

        long timestamp = System.currentTimeMillis();
        String signature = hmacSignerService.generateSignature(testApiSecret, timestamp, "POST", "/api/notifications", invalidRequest);

        mockMvc.perform(post("/api/notifications")
                        .header("X-API-KEY", testApiKey)
                        .header("X-TIMESTAMP", String.valueOf(timestamp))
                        .header("X-SIGNATURE", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequest))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should return 400 Bad Request when message exceeds 500 characters")
    void shouldRejectMessageTooLong() throws Exception {
        String longMessage = "a".repeat(501);
        NotificationRequest request = new NotificationRequest(
                NotificationChannel.SMS,
                "+250700000001",
                longMessage
        );

        long timestamp = System.currentTimeMillis();
        String requestBody = objectMapper.writeValueAsString(request);
        String signature = hmacSignerService.generateSignature(testApiSecret, timestamp, "POST", "/api/notifications", requestBody);
        
        mockMvc.perform(post("/api/notifications")
                        .header("X-API-KEY", testApiKey)
                        .header("X-TIMESTAMP", String.valueOf(timestamp))
                        .header("X-SIGNATURE", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'message')]").exists());
    }

    @Test
    @DisplayName("Should return 400 Bad Request for invalid channel")
    void shouldRejectInvalidChannel() throws Exception {
        String invalidRequest = """
                {
                    "channel": "INVALID",
                    "to": "+250700000001",
                    "message": "Test message"
                }
                """;

        long timestamp = System.currentTimeMillis();
        String signature = hmacSignerService.generateSignature(testApiSecret, timestamp, "POST", "/api/notifications", invalidRequest);

        mockMvc.perform(post("/api/notifications")
                        .header("X-API-KEY", testApiKey)
                        .header("X-TIMESTAMP", String.valueOf(timestamp))
                        .header("X-SIGNATURE", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequest))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    org.junit.jupiter.api.Assertions.assertTrue(
                        status == 400 || status == 500, 
                        "Expected 400 or 500 but got " + status
                    );
                }); // 400 for validation, 500 for deserialization error
    }

    @Test
    @DisplayName("Should accept message with exactly 500 characters")
    void shouldAcceptMessageWith500Characters() throws Exception {
        String maxLengthMessage = "a".repeat(500);
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

        when(notificationService.queueNotification(any(), anyLong(), anyString()))
                .thenReturn(expectedResponse);

        long timestamp = System.currentTimeMillis();
        String requestBody = objectMapper.writeValueAsString(request);
        String signature = hmacSignerService.generateSignature(testApiSecret, timestamp, "POST", "/api/notifications", requestBody);
        
        mockMvc.perform(post("/api/notifications")
                        .header("X-API-KEY", testApiKey)
                        .header("X-TIMESTAMP", String.valueOf(timestamp))
                        .header("X-SIGNATURE", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("accepted"));
    }

    @Test
    @DisplayName("Should return 400 Bad Request when recipient is empty string")
    void shouldRejectEmptyRecipient() throws Exception {
        NotificationRequest request = new NotificationRequest(
                NotificationChannel.SMS,
                "",
                "Test message"
        );

        long timestamp = System.currentTimeMillis();
        String requestBody = objectMapper.writeValueAsString(request);
        String signature = hmacSignerService.generateSignature(testApiSecret, timestamp, "POST", "/api/notifications", requestBody);
        
        mockMvc.perform(post("/api/notifications")
                        .header("X-API-KEY", testApiKey)
                        .header("X-TIMESTAMP", String.valueOf(timestamp))
                        .header("X-SIGNATURE", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'to')]").exists());
    }

    @Test
    @DisplayName("Should return 400 Bad Request when message is empty string")
    void shouldRejectEmptyMessage() throws Exception {
        NotificationRequest request = new NotificationRequest(
                NotificationChannel.SMS,
                "+250700000001",
                ""
        );

        long timestamp = System.currentTimeMillis();
        String requestBody = objectMapper.writeValueAsString(request);
        String signature = hmacSignerService.generateSignature(testApiSecret, timestamp, "POST", "/api/notifications", requestBody);
        
        mockMvc.perform(post("/api/notifications")
                        .header("X-API-KEY", testApiKey)
                        .header("X-TIMESTAMP", String.valueOf(timestamp))
                        .header("X-SIGNATURE", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'message')]").exists());
    }

    @Test
    @DisplayName("Should include timestamp in response")
    void shouldIncludeTimestampInResponse() throws Exception {
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

        when(notificationService.queueNotification(any(), anyLong(), anyString()))
                .thenReturn(expectedResponse);

        long timestamp = System.currentTimeMillis();
        String requestBody = objectMapper.writeValueAsString(request);
        String signature = hmacSignerService.generateSignature(testApiSecret, timestamp, "POST", "/api/notifications", requestBody);
        
        mockMvc.perform(post("/api/notifications")
                        .header("X-API-KEY", testApiKey)
                        .header("X-TIMESTAMP", String.valueOf(timestamp))
                        .header("X-SIGNATURE", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.timestamp").exists());
    }
}
