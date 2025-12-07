package com.irembo.notifications.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class HMACSignerServiceTest {

    private HMACSignerService service;

    @BeforeEach
    void setUp() {
        service = new HMACSignerService();
    }

    @Test
    @DisplayName("Should generate signature successfully")
    void shouldGenerateSignatureSuccessfully() {
        String apiSecret = "test-secret-key-12345678901234567890123456789012";
        long timestamp = 1234567890L;
        String httpMethod = "POST";
        String requestPath = "/api/notifications";
        String requestBody = "{\"channel\":\"SMS\",\"to\":\"+1234567890\",\"message\":\"test\"}";

        String signature = service.generateSignature(apiSecret, timestamp, httpMethod, requestPath, requestBody);

        assertThat(signature).isNotNull();
        assertThat(signature).isNotEmpty();
    }

    @Test
    @DisplayName("Should generate same signature for same inputs")
    void shouldGenerateSameSignatureForSameInputs() {
        String apiSecret = "test-secret-key-12345678901234567890123456789012";
        long timestamp = 1234567890L;
        String httpMethod = "POST";
        String requestPath = "/api/notifications";
        String requestBody = "{\"channel\":\"SMS\"}";

        String signature1 = service.generateSignature(apiSecret, timestamp, httpMethod, requestPath, requestBody);
        String signature2 = service.generateSignature(apiSecret, timestamp, httpMethod, requestPath, requestBody);

        assertThat(signature1).isEqualTo(signature2);
    }

    @Test
    @DisplayName("Should generate different signatures for different timestamps")
    void shouldGenerateDifferentSignaturesForDifferentTimestamps() {
        String apiSecret = "test-secret-key-12345678901234567890123456789012";
        String httpMethod = "POST";
        String requestPath = "/api/notifications";
        String requestBody = "{\"channel\":\"SMS\"}";

        String signature1 = service.generateSignature(apiSecret, 1000L, httpMethod, requestPath, requestBody);
        String signature2 = service.generateSignature(apiSecret, 2000L, httpMethod, requestPath, requestBody);

        assertThat(signature1).isNotEqualTo(signature2);
    }

    @Test
    @DisplayName("Should handle null request body")
    void shouldHandleNullRequestBody() {
        String apiSecret = "test-secret-key-12345678901234567890123456789012";
        long timestamp = 1234567890L;
        String httpMethod = "GET";
        String requestPath = "/api/notifications";

        String signature = service.generateSignature(apiSecret, timestamp, httpMethod, requestPath, null);

        assertThat(signature).isNotNull();
    }

    @Test
    @DisplayName("Should handle empty request body")
    void shouldHandleEmptyRequestBody() {
        String apiSecret = "test-secret-key-12345678901234567890123456789012";
        long timestamp = 1234567890L;
        String httpMethod = "GET";
        String requestPath = "/api/notifications";

        String signature = service.generateSignature(apiSecret, timestamp, httpMethod, requestPath, "");

        assertThat(signature).isNotNull();
    }

    @Test
    @DisplayName("Should normalize path by removing query parameters")
    void shouldNormalizePathByRemovingQueryParameters() {
        String apiSecret = "test-secret-key-12345678901234567890123456789012";
        long timestamp = 1234567890L;
        String httpMethod = "GET";
        String requestPath = "/api/notifications?param=value";
        String requestBody = "";

        String signature1 = service.generateSignature(apiSecret, timestamp, httpMethod, requestPath, requestBody);
        String signature2 = service.generateSignature(apiSecret, timestamp, httpMethod, "/api/notifications", requestBody);

        assertThat(signature1).isEqualTo(signature2);
    }

    @Test
    @DisplayName("Should throw exception when apiSecret is null")
    void shouldThrowExceptionWhenApiSecretIsNull() {
        assertThatThrownBy(() -> service.generateSignature(null, 1234567890L, "POST", "/api/notifications", "{}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("API secret cannot be null or blank");
    }

    @Test
    @DisplayName("Should throw exception when apiSecret is blank")
    void shouldThrowExceptionWhenApiSecretIsBlank() {
        assertThatThrownBy(() -> service.generateSignature("   ", 1234567890L, "POST", "/api/notifications", "{}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("API secret cannot be null or blank");
    }

    @Test
    @DisplayName("Should throw exception when httpMethod is null")
    void shouldThrowExceptionWhenHttpMethodIsNull() {
        assertThatThrownBy(() -> service.generateSignature("secret", 1234567890L, null, "/api/notifications", "{}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("HTTP method cannot be null or blank");
    }

    @Test
    @DisplayName("Should throw exception when requestPath is null")
    void shouldThrowExceptionWhenRequestPathIsNull() {
        assertThatThrownBy(() -> service.generateSignature("secret", 1234567890L, "POST", null, "{}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Request path cannot be null");
    }

    @Test
    @DisplayName("Should verify signature successfully")
    void shouldVerifySignatureSuccessfully() {
        String apiSecret = "test-secret-key-12345678901234567890123456789012";
        long timestamp = 1234567890L;
        String httpMethod = "POST";
        String requestPath = "/api/notifications";
        String requestBody = "{\"channel\":\"SMS\"}";

        String signature = service.generateSignature(apiSecret, timestamp, httpMethod, requestPath, requestBody);
        boolean isValid = service.verifySignature(apiSecret, timestamp, httpMethod, requestPath, requestBody, signature);

        assertThat(isValid).isTrue();
    }

    @Test
    @DisplayName("Should reject invalid signature")
    void shouldRejectInvalidSignature() {
        String apiSecret = "test-secret-key-12345678901234567890123456789012";
        long timestamp = 1234567890L;
        String httpMethod = "POST";
        String requestPath = "/api/notifications";
        String requestBody = "{\"channel\":\"SMS\"}";

        boolean isValid = service.verifySignature(apiSecret, timestamp, httpMethod, requestPath, requestBody, "invalid-signature");

        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("Should return false when apiSecret is null for verification")
    void shouldReturnFalseWhenApiSecretIsNullForVerification() {
        boolean isValid = service.verifySignature(null, 1234567890L, "POST", "/api/notifications", "{}", "signature");
        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("Should return false when providedSignature is null for verification")
    void shouldReturnFalseWhenProvidedSignatureIsNullForVerification() {
        boolean isValid = service.verifySignature("secret", 1234567890L, "POST", "/api/notifications", "{}", null);
        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("Should handle different HTTP methods")
    void shouldHandleDifferentHttpMethods() {
        String apiSecret = "test-secret-key-12345678901234567890123456789012";
        long timestamp = 1234567890L;
        String requestPath = "/api/notifications";
        String requestBody = "{}";

        String postSignature = service.generateSignature(apiSecret, timestamp, "POST", requestPath, requestBody);
        String getSignature = service.generateSignature(apiSecret, timestamp, "GET", requestPath, requestBody);

        assertThat(postSignature).isNotEqualTo(getSignature);
    }

    @Test
    @DisplayName("Should uppercase HTTP method in signature")
    void shouldUppercaseHttpMethodInSignature() {
        String apiSecret = "test-secret-key-12345678901234567890123456789012";
        long timestamp = 1234567890L;
        String requestPath = "/api/notifications";
        String requestBody = "{}";

        String upperSignature = service.generateSignature(apiSecret, timestamp, "POST", requestPath, requestBody);
        String lowerSignature = service.generateSignature(apiSecret, timestamp, "post", requestPath, requestBody);

        assertThat(upperSignature).isEqualTo(lowerSignature);
    }
}

