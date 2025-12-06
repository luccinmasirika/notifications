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
import com.irembo.notifications.config.FilterPathMatcher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

@Component
@Order(2)
public class RateLimiterFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(RateLimiterFilter.class);

    private static final String API_KEY_HEADER = "X-API-KEY";

    private final RateLimiterService rateLimiterService;
    private final ObjectMapper objectMapper;
    private final FilterPathMatcher filterPathMatcher;
    private final Random random;

    @Value("${app.rate-limiter.jitter.min-ms:50}")
    private int jitterMinMs;

    @Value("${app.rate-limiter.jitter.max-ms:200}")
    private int jitterMaxMs;

    public RateLimiterFilter(
            RateLimiterService rateLimiterService, 
            ObjectMapper objectMapper,
            FilterPathMatcher filterPathMatcher) {
        this.rateLimiterService = rateLimiterService;
        this.objectMapper = objectMapper;
        this.filterPathMatcher = filterPathMatcher;
        this.random = new Random();
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();
        if (filterPathMatcher.shouldSkip(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        String apiKey = request.getHeader(API_KEY_HEADER);
        if (apiKey == null || apiKey.isBlank()) {
            sendUnauthorizedResponse(response, "Missing X-API-KEY header");
            return;
        }

        RateDecision decision = rateLimiterService.checkAndConsume(apiKey);

        setRateLimitHeaders(response, decision);

        if (decision.type() == DecisionType.HARD_REJECT) {
            handleHardReject(response, decision);
            return;
        }

        if (decision.type() == DecisionType.SOFT_THROTTLE) {
            handleSoftThrottle(decision);
        }

        filterChain.doFilter(request, response);
    }

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

    private void handleHardReject(HttpServletResponse response, RateDecision decision) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

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

    private void handleSoftThrottle(RateDecision decision) {
        int jitterMs = jitterMinMs + random.nextInt(jitterMaxMs - jitterMinMs);

        try {
            logger.info("Soft throttle applied: {}ms delay, usage: {}%", jitterMs, String.format("%.2f", decision.usagePercent()));
            Thread.sleep(jitterMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Soft throttle interrupted", e);
        }
    }

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
