package com.irembo.notifications.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.irembo.notifications.config.FilterPathMatcher;
import com.irembo.notifications.model.dto.RateDecision;
import com.irembo.notifications.model.enums.DecisionType;
import com.irembo.notifications.service.RateLimiterService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimiterFilterTest {

    @Mock
    private RateLimiterService rateLimiterService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    private RateLimiterFilter filter;
    private StringWriter responseWriter;

    @BeforeEach
    void setUp() throws Exception {
        ObjectMapper testObjectMapper = new ObjectMapper();
        testObjectMapper.registerModule(new JavaTimeModule());
        
        FilterPathMatcher filterPathMatcher = FilterPathMatcher.forTesting("/health,/actuator/health,/actuator/**,/admin/**,/swagger-ui**,/v3/api-docs**,/api-docs**,/error**");
        
        filter = new RateLimiterFilter(rateLimiterService, testObjectMapper, filterPathMatcher);
        
        // Set jitter values to prevent "bound must be positive" error
        ReflectionTestUtils.setField(filter, "jitterMinMs", 50);
        ReflectionTestUtils.setField(filter, "jitterMaxMs", 200);

        responseWriter = new StringWriter();
        lenient().when(response.getWriter()).thenReturn(new PrintWriter(responseWriter));
    }

    @Test
    @DisplayName("Should skip rate limiting for health endpoint")
    void shouldSkipRateLimitingForHealthEndpoint() throws Exception {
        when(request.getRequestURI()).thenReturn("/health");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(rateLimiterService, never()).checkAndConsume(anyString());
    }

    @Test
    @DisplayName("Should return 401 when API key is missing")
    void shouldReturn401WhenApiKeyMissing() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/notifications");
        when(request.getHeader("X-API-KEY")).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(401);
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    @DisplayName("Should allow request when rate limit decision is ALLOW")
    void shouldAllowRequestWhenRateLimitDecisionIsAllow() throws Exception {
        RateDecision decision = RateDecision.allow(100, 50, Instant.now().plusSeconds(60), 50.0);

        when(request.getRequestURI()).thenReturn("/api/notifications");
        when(request.getHeader("X-API-KEY")).thenReturn("test-api-key");
        when(rateLimiterService.checkAndConsume("test-api-key")).thenReturn(decision);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(response).setHeader("X-RateLimit-Limit", "100");
        verify(response).setHeader("X-RateLimit-Remaining", "50");
        verify(response, never()).setStatus(429);
    }

    @Test
    @DisplayName("Should allow request when rate limit decision is SOFT_THROTTLE")
    void shouldAllowRequestWhenRateLimitDecisionIsSoftThrottle() throws Exception {
        RateDecision decision = RateDecision.softThrottle(100, 20, Instant.now().plusSeconds(60), 80.0);

        when(request.getRequestURI()).thenReturn("/api/notifications");
        when(request.getHeader("X-API-KEY")).thenReturn("test-api-key");
        when(rateLimiterService.checkAndConsume("test-api-key")).thenReturn(decision);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(response).setHeader("X-Soft-Throttled", "true");
        verify(response, never()).setStatus(429);
    }

    @Test
    @DisplayName("Should reject request when rate limit decision is HARD_REJECT")
    void shouldRejectRequestWhenRateLimitDecisionIsHardReject() throws Exception {
        RateDecision decision = RateDecision.hardReject(100, Instant.now().plusSeconds(60), 100.0);

        when(request.getRequestURI()).thenReturn("/api/notifications");
        when(request.getHeader("X-API-KEY")).thenReturn("test-api-key");
        when(rateLimiterService.checkAndConsume("test-api-key")).thenReturn(decision);

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(429);
        verify(response).setHeader(eq("Retry-After"), anyString());
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    @DisplayName("Should set rate limit headers correctly")
    void shouldSetRateLimitHeadersCorrectly() throws Exception {
        Instant resetTime = Instant.now().plusSeconds(60);
        RateDecision decision = RateDecision.allow(100, 50, resetTime, 50.0);

        when(request.getRequestURI()).thenReturn("/api/notifications");
        when(request.getHeader("X-API-KEY")).thenReturn("test-api-key");
        when(rateLimiterService.checkAndConsume("test-api-key")).thenReturn(decision);

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setHeader("X-RateLimit-Limit", "100");
        verify(response).setHeader("X-RateLimit-Remaining", "50");
        verify(response).setHeader("X-RateLimit-Reset", String.valueOf(resetTime.getEpochSecond()));
    }
}

