package com.irembo.notifications.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.irembo.notifications.infra.db.entity.Client;
import com.irembo.notifications.infra.db.repository.ClientRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class APIKeyAuthFilterTest {

    @Mock
    private ClientRepository clientRepository;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    private APIKeyAuthFilter filter;

    private StringWriter responseWriter;

    @BeforeEach
    void setUp() throws Exception {
        // Use a real ObjectMapper for testing with JSR310 support to ensure proper JSON serialization
        ObjectMapper testObjectMapper = new ObjectMapper();
        testObjectMapper.registerModule(new JavaTimeModule());
        filter = new APIKeyAuthFilter(clientRepository, testObjectMapper);

        // Setup response writer (lenient as not all tests write responses)
        responseWriter = new StringWriter();
        lenient().when(response.getWriter()).thenReturn(new PrintWriter(responseWriter));
    }

    @Test
    @DisplayName("Should skip authentication for health endpoint")
    void shouldSkipAuthForHealthEndpoint() throws Exception {
        // Given
        when(request.getRequestURI()).thenReturn("/health");

        // When
        filter.doFilterInternal(request, response, filterChain);

        // Then
        verify(filterChain).doFilter(request, response);
        verify(clientRepository, never()).findByApiKey(anyString());
    }

    @Test
    @DisplayName("Should skip authentication for admin endpoints")
    void shouldSkipAuthForAdminEndpoints() throws Exception {
        // Given
        when(request.getRequestURI()).thenReturn("/admin/clients");

        // When
        filter.doFilterInternal(request, response, filterChain);

        // Then
        verify(filterChain).doFilter(request, response);
        verify(clientRepository, never()).findByApiKey(anyString());
    }

    @Test
    @DisplayName("Should return 401 when X-API-KEY header is missing")
    void shouldReturn401WhenApiKeyMissing() throws Exception {
        // Given
        when(request.getRequestURI()).thenReturn("/api/notifications");
        when(request.getHeader("X-API-KEY")).thenReturn(null);

        // When
        filter.doFilterInternal(request, response, filterChain);

        // Then
        verify(response).setStatus(401);
        verify(response).setContentType("application/json");
        verify(filterChain, never()).doFilter(request, response);

        String responseBody = responseWriter.toString();
        assertThat(responseBody).contains("Unauthorized");
        assertThat(responseBody).contains("Missing X-API-KEY header");
    }

    @Test
    @DisplayName("Should return 401 when API key is invalid")
    void shouldReturn401WhenApiKeyInvalid() throws Exception {
        // Given
        when(request.getRequestURI()).thenReturn("/api/notifications");
        when(request.getHeader("X-API-KEY")).thenReturn("invalid-key");
        when(clientRepository.findByApiKey("invalid-key")).thenReturn(Optional.empty());

        // When
        filter.doFilterInternal(request, response, filterChain);

        // Then
        verify(response).setStatus(401);
        verify(filterChain, never()).doFilter(request, response);

        String responseBody = responseWriter.toString();
        assertThat(responseBody).contains("Invalid API key");
    }

    @Test
    @DisplayName("Should return 401 when client is inactive")
    void shouldReturn401WhenClientInactive() throws Exception {
        // Given
        Client inactiveClient = new Client();
        inactiveClient.setId(1L);
        inactiveClient.setApiKey("test-key");
        inactiveClient.setActive(false);

        when(request.getRequestURI()).thenReturn("/api/notifications");
        when(request.getHeader("X-API-KEY")).thenReturn("test-key");
        when(clientRepository.findByApiKey("test-key")).thenReturn(Optional.of(inactiveClient));

        // When
        filter.doFilterInternal(request, response, filterChain);

        // Then
        verify(response).setStatus(401);
        verify(filterChain, never()).doFilter(request, response);

        String responseBody = responseWriter.toString();
        assertThat(responseBody).contains("Client account is inactive");
    }

    @Test
    @DisplayName("Should allow request when API key is valid and client is active")
    void shouldAllowRequestWhenApiKeyValid() throws Exception {
        // Given
        Client activeClient = new Client();
        activeClient.setId(1L);
        activeClient.setApiKey("valid-key");
        activeClient.setName("Test Client");
        activeClient.setActive(true);

        when(request.getRequestURI()).thenReturn("/api/notifications");
        when(request.getHeader("X-API-KEY")).thenReturn("valid-key");
        when(clientRepository.findByApiKey("valid-key")).thenReturn(Optional.of(activeClient));

        // When
        filter.doFilterInternal(request, response, filterChain);

        // Then
        verify(filterChain).doFilter(request, response);
        verify(request).setAttribute("authenticatedClient", activeClient);
        verify(request).setAttribute("clientId", 1L);
        verify(request).setAttribute("clientName", "Test Client");
        verify(response, never()).setStatus(401);
    }
}
