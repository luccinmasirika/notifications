package com.irembo.notifications.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.irembo.notifications.model.dto.ErrorResponse;
import com.irembo.notifications.model.dto.RateDecision;
import com.irembo.notifications.model.enums.DecisionType;
import com.irembo.notifications.service.RateLimiterService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

@Component
@Order(2) // Run after APIKeyAuthFilter
public class RateLimiterFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(RateLimiterFilter.class);

    private static final String API_KEY_HEADER = "X-API-KEY";
    private static final int JITTER_MIN_MS = 50;
    private static final int JITTER_MAX_MS = 200;

    private final RateLimiterService rateLimiterService;
    private final ObjectMapper objectMapper;
    private final Random random;

    public RateLimiterFilter(RateLimiterService rateLimiterService, ObjectMapper objectMapper) {
        this.rateLimiterService = rateLimiterService;
        this.objectMapper = objectMapper;
        this.random = new Random();
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        // Skip rate limiting for health check and other non-API endpoints
        String path = request.getRequestURI();
        if (shouldSkipRateLimiting(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Extract API key from header
        String apiKey = request.getHeader(API_KEY_HEADER);
        if (apiKey == null || apiKey.isBlank()) {
            sendUnauthorizedResponse(response, "Missing X-API-KEY header");
            return;
        }

        // Extract channel from request (default to "GENERAL")
        String channel = extractChannel(request);

        // Check rate limits
        RateDecision decision = rateLimiterService.checkAndConsume(apiKey, channel);

        // Always set rate limit headers
        setRateLimitHeaders(response, decision);

        // Handle decision
        if (decision.type() == DecisionType.HARD_REJECT) {
            handleHardReject(response, decision);
            return;
        }

        if (decision.type() == DecisionType.SOFT_THROTTLE) {
            handleSoftThrottle(decision);
        }

        // Continue with the request
        filterChain.doFilter(request, response);
    }

    /**
     * Determine if rate limiting should be skipped for this path.
     * Skip for: health checks, actuator endpoints, admin endpoints, swagger/openapi, error pages.
     */
    private boolean shouldSkipRateLimiting(String path) {
        return path.equals("/health") ||
               path.equals("/actuator/health") ||
               path.startsWith("/actuator/") ||
               path.startsWith("/admin/") ||
               path.startsWith("/swagger-ui") ||
               path.startsWith("/v3/api-docs") ||
               path.startsWith("/api-docs") ||
               path.startsWith("/error");
    }

    /**
     * Extract notification channel from request.
     * This could come from query param, request body, or default to "GENERAL".
     */
    private String extractChannel(HttpServletRequest request) {
        String channel = request.getParameter("channel");
        if (channel != null && !channel.isBlank()) {
            return channel.toUpperCase();
        }
        return "GENERAL";
    }

    /**
     * Set rate limit headers on the response.
     */
    private void setRateLimitHeaders(HttpServletResponse response, RateDecision decision) {
        response.setHeader("X-RateLimit-Limit", String.valueOf(decision.limit()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(decision.remaining()));
        response.setHeader("X-RateLimit-Reset", String.valueOf(decision.reset().getEpochSecond()));

        if (decision.type() == DecisionType.SOFT_THROTTLE) {
            response.setHeader("X-Soft-Throttled", String.valueOf(true));
        }
        
        if (decision.type() == DecisionType.HARD_REJECT && decision.retryAt() != null) {
            long retryAfterSeconds = decision.retryAt().getEpochSecond() - Instant.now().getEpochSecond();
            response.setHeader("Retry-After", String.valueOf(Math.max(0, retryAfterSeconds)));
        }
    }

    /**
     * Handle hard reject - return 429 with JSON body.
     */
    private void handleHardReject(HttpServletResponse response, RateDecision decision) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        // Use standardized error response
        ErrorResponse errorResponse = ErrorResponse.of(
                "Rate limit exceeded",
                String.format("Rate limit exceeded. Usage: %.2f%%", decision.usagePercent()),
                429
        );

        String jsonResponse = objectMapper.writeValueAsString(errorResponse);
        response.getWriter().write(jsonResponse);
        response.getWriter().flush();

        logger.warn("Rate limit hard reject: usage: {}%", String.format("%.2f", decision.usagePercent()));
    }

    /**
     * Handle soft throttle - add jitter delay.
     */
    private void handleSoftThrottle(RateDecision decision) {
        int jitterMs = JITTER_MIN_MS + random.nextInt(JITTER_MAX_MS - JITTER_MIN_MS);

        try {
            logger.info("Soft throttle applied: {}ms delay, usage: {}%", jitterMs, String.format("%.2f", decision.usagePercent()));
            Thread.sleep(jitterMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Soft throttle interrupted", e);
        }
    }

    /**
     * Send 401 Unauthorized response.
     */
    private void sendUnauthorizedResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        Map<String, Object> errorBody = new HashMap<>();
        errorBody.put("error", "Unauthorized");
        errorBody.put("status", 401);
        errorBody.put("message", message);

        String jsonResponse = objectMapper.writeValueAsString(errorBody);
        response.getWriter().write(jsonResponse);
        response.getWriter().flush();
    }
}
