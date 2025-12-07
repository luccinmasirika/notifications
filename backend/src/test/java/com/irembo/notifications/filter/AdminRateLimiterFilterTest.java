package com.irembo.notifications.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.irembo.notifications.infra.redis.RedisCounterRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminRateLimiterFilterTest {

    @Mock
    private RedisCounterRepository redisCounter;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private AdminRateLimiterFilter filter;

    private StringWriter responseWriter;

    @BeforeEach
    void setUp() throws Exception {
        responseWriter = new StringWriter();
        lenient().when(response.getWriter()).thenReturn(new PrintWriter(responseWriter));
        
        // Set default values using reflection
        ReflectionTestUtils.setField(filter, "windowSeconds", 300);
        ReflectionTestUtils.setField(filter, "maxRequests", 100L);
        ReflectionTestUtils.setField(filter, "rateLimitKeyPrefix", "admin:ratelimit:");
        ReflectionTestUtils.setField(filter, "ipVisibleChars", 8);
    }

    @Test
    @DisplayName("Should skip rate limiting for non-admin paths")
    void shouldSkipRateLimitingForNonAdminPaths() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/notifications");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(redisCounter, never()).incrementWindowCounter(anyString(), anyInt());
    }

    @Test
    @DisplayName("Should apply rate limiting for admin paths")
    void shouldApplyRateLimitingForAdminPaths() throws Exception {
        when(request.getRequestURI()).thenReturn("/admin/clients");
        when(request.getRemoteAddr()).thenReturn("192.168.1.1");
        when(redisCounter.incrementWindowCounter(anyString(), anyInt())).thenReturn(1L);
        when(redisCounter.getWindowResetTime(anyInt())).thenReturn(Instant.now().plusSeconds(300));

        filter.doFilterInternal(request, response, filterChain);

        verify(redisCounter).incrementWindowCounter(anyString(), eq(300));
        verify(response).setHeader(eq("X-RateLimit-Limit"), eq("100"));
        verify(response).setHeader(eq("X-RateLimit-Remaining"), anyString());
        verify(response).setHeader(eq("X-RateLimit-Reset"), anyString());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Should block request when rate limit exceeded")
    void shouldBlockRequestWhenRateLimitExceeded() throws Exception {
        when(request.getRequestURI()).thenReturn("/admin/clients");
        when(request.getRemoteAddr()).thenReturn("192.168.1.1");
        when(redisCounter.incrementWindowCounter(anyString(), anyInt())).thenReturn(101L);
        when(redisCounter.getWindowResetTime(anyInt())).thenReturn(Instant.now().plusSeconds(300));
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"error\":\"Too Many Requests\"}");

        filter.doFilterInternal(request, response, filterChain);

        verify(redisCounter).incrementWindowCounter(anyString(), eq(300));
        verify(response).setStatus(429);
        verify(response).setContentType("application/json");
        verify(response).setHeader(eq("Retry-After"), anyString());
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("Should use X-Forwarded-For header when present")
    void shouldUseXForwardedForHeaderWhenPresent() throws Exception {
        when(request.getRequestURI()).thenReturn("/admin/clients");
        when(request.getHeader("X-Forwarded-For")).thenReturn("10.0.0.1, 192.168.1.1");
        when(redisCounter.incrementWindowCounter(anyString(), anyInt())).thenReturn(1L);
        when(redisCounter.getWindowResetTime(anyInt())).thenReturn(Instant.now().plusSeconds(300));

        filter.doFilterInternal(request, response, filterChain);

        verify(redisCounter).incrementWindowCounter(eq("admin:ratelimit:10.0.0.1"), eq(300));
    }

    @Test
    @DisplayName("Should use remote address when X-Forwarded-For is missing")
    void shouldUseRemoteAddressWhenXForwardedForMissing() throws Exception {
        when(request.getRequestURI()).thenReturn("/admin/clients");
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("192.168.1.1");
        when(redisCounter.incrementWindowCounter(anyString(), anyInt())).thenReturn(1L);
        when(redisCounter.getWindowResetTime(anyInt())).thenReturn(Instant.now().plusSeconds(300));

        filter.doFilterInternal(request, response, filterChain);

        verify(redisCounter).incrementWindowCounter(eq("admin:ratelimit:192.168.1.1"), eq(300));
    }

    @Test
    @DisplayName("Should mask IP address correctly for IPv4")
    void shouldMaskIpAddressCorrectlyForIPv4() throws Exception {
        when(request.getRequestURI()).thenReturn("/admin/clients");
        when(request.getRemoteAddr()).thenReturn("192.168.1.1");
        when(redisCounter.incrementWindowCounter(anyString(), anyInt())).thenReturn(80L);
        when(redisCounter.getWindowResetTime(anyInt())).thenReturn(Instant.now().plusSeconds(300));

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Should set correct remaining count header")
    void shouldSetCorrectRemainingCountHeader() throws Exception {
        when(request.getRequestURI()).thenReturn("/admin/clients");
        when(request.getRemoteAddr()).thenReturn("192.168.1.1");
        when(redisCounter.incrementWindowCounter(anyString(), anyInt())).thenReturn(50L);
        when(redisCounter.getWindowResetTime(anyInt())).thenReturn(Instant.now().plusSeconds(300));

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setHeader("X-RateLimit-Remaining", "50");
    }
}
