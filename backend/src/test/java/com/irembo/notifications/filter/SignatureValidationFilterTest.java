package com.irembo.notifications.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.irembo.notifications.config.FilterPathMatcher;
import com.irembo.notifications.infra.db.entity.Client;
import com.irembo.notifications.service.CryptoService;
import com.irembo.notifications.service.HMACSignerService;
import com.irembo.notifications.service.TimestampValidator;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SignatureValidationFilterTest {

    @Mock
    private CryptoService cryptoService;

    @Mock
    private HMACSignerService hmacSignerService;

    @Mock
    private TimestampValidator timestampValidator;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    private SignatureValidationFilter filter;
    private StringWriter responseWriter;

    @BeforeEach
    void setUp() throws Exception {
        ObjectMapper testObjectMapper = new ObjectMapper();
        testObjectMapper.registerModule(new JavaTimeModule());
        
        FilterPathMatcher filterPathMatcher = FilterPathMatcher.forTesting("/health,/actuator/health,/actuator/**,/admin/**,/swagger-ui**,/v3/api-docs**,/api-docs**,/error**");
        
        filter = new SignatureValidationFilter(
                cryptoService,
                hmacSignerService,
                timestampValidator,
                testObjectMapper,
                filterPathMatcher
        );

        responseWriter = new StringWriter();
        lenient().when(response.getWriter()).thenReturn(new PrintWriter(responseWriter));
    }

    @Test
    @DisplayName("Should skip validation for health endpoint")
    void shouldSkipValidationForHealthEndpoint() throws Exception {
        when(request.getRequestURI()).thenReturn("/health");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(hmacSignerService, never()).verifySignature(anyString(), anyLong(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Should return 401 when X-API-KEY header is missing")
    void shouldReturn401WhenApiKeyMissing() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/notifications");
        when(request.getHeader("X-API-KEY")).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(401);
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    @DisplayName("Should return 401 when X-TIMESTAMP header is missing")
    void shouldReturn401WhenTimestampMissing() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/notifications");
        when(request.getHeader("X-API-KEY")).thenReturn("test-key");
        when(request.getHeader("X-TIMESTAMP")).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(401);
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    @DisplayName("Should return 401 when X-SIGNATURE header is missing")
    void shouldReturn401WhenSignatureMissing() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/notifications");
        when(request.getHeader("X-API-KEY")).thenReturn("test-key");
        when(request.getHeader("X-TIMESTAMP")).thenReturn("1234567890");
        when(request.getHeader("X-SIGNATURE")).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(401);
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    @DisplayName("Should return 401 when timestamp is invalid")
    void shouldReturn401WhenTimestampInvalid() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/notifications");
        when(request.getHeader("X-API-KEY")).thenReturn("test-key");
        when(request.getHeader("X-TIMESTAMP")).thenReturn("1234567890");
        when(request.getHeader("X-SIGNATURE")).thenReturn("test-signature");
        when(timestampValidator.isValid(1234567890L)).thenReturn(false);

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(401);
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    @DisplayName("Should return 401 when signature is invalid")
    void shouldReturn401WhenSignatureInvalid() throws Exception {
        Client client = new Client();
        client.setId(1L);
        client.setApiSecretEncrypted("encrypted-secret");

        when(request.getRequestURI()).thenReturn("/api/notifications");
        when(request.getHeader("X-API-KEY")).thenReturn("test-key");
        long timestamp = System.currentTimeMillis();
        when(request.getHeader("X-TIMESTAMP")).thenReturn(String.valueOf(timestamp));
        when(request.getHeader("X-SIGNATURE")).thenReturn("invalid-signature");
        when(timestampValidator.isValid(timestamp)).thenReturn(true);
        when(request.getAttribute("authenticatedClient")).thenReturn(client);
        when(cryptoService.decrypt("encrypted-secret")).thenReturn("decrypted-secret");
        lenient().when(hmacSignerService.verifySignature(anyString(), eq(timestamp), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(false);

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(401);
        verify(filterChain, never()).doFilter(request, response);
    }
}

