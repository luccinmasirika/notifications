package com.irembo.notifications.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.irembo.notifications.infra.redis.RedisCounterRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

/**
 * Rate limiter for admin endpoints.
 * Protects against brute-force attacks on admin authentication.
 *
 * Limits: Configurable requests per IP per configurable window on /admin/** endpoints
 *
 * This prevents:
 * - Brute force password attacks
 * - Excessive polling of admin endpoints
 * - DoS attacks on admin interface
 */
@Component
@Order(0) // Run before APIKeyAuthFilter and other filters
public class AdminRateLimiterFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(AdminRateLimiterFilter.class);

    @Value("${app.admin-rate-limiter.window-seconds:300}")
    private int windowSeconds;

    @Value("${app.admin-rate-limiter.max-requests:100}")
    private long maxRequests;

    @Value("${app.redis.key-prefix.admin-rate-limit:admin:ratelimit:}")
    private String rateLimitKeyPrefix;

    @Value("${app.filter.masking.ip-visible-chars:8}")
    private int ipVisibleChars;

    private final RedisCounterRepository redisCounter;
    private final ObjectMapper objectMapper;

    public AdminRateLimiterFilter(RedisCounterRepository redisCounter, ObjectMapper objectMapper) {
        this.redisCounter = redisCounter;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();

        // Only apply to admin endpoints
        if (!path.startsWith("/admin/")) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = getClientIp(request);
        String rateLimitKey = rateLimitKeyPrefix + clientIp;

        // Check and increment counter
        long count = redisCounter.incrementWindowCounter(rateLimitKey, windowSeconds);

        // Add rate limit headers
        response.setHeader("X-RateLimit-Limit", String.valueOf(maxRequests));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(Math.max(0, maxRequests - count)));

        Instant resetTime = redisCounter.getWindowResetTime(windowSeconds);
        response.setHeader("X-RateLimit-Reset", String.valueOf(resetTime.getEpochSecond()));

        // Check if limit exceeded
        if (count > maxRequests) {
            logger.warn("Admin rate limit exceeded for IP: {} (count: {})", maskIp(clientIp), count);
            sendRateLimitExceededResponse(response, resetTime);
            return;
        }

        // Log warning when approaching limit (80%)
        if (count > (maxRequests * 0.8)) {
            logger.info("Admin IP {} approaching rate limit: {}/{}", maskIp(clientIp), count, maxRequests);
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Extract client IP address from request.
     * Handles X-Forwarded-For header for proxied requests.
     */
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            // Take first IP in the chain (original client)
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * Send 429 Too Many Requests response.
     */
    private void sendRateLimitExceededResponse(HttpServletResponse response, Instant resetTime) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        long retryAfterSeconds = resetTime.getEpochSecond() - Instant.now().getEpochSecond();
        response.setHeader("Retry-After", String.valueOf(Math.max(0, retryAfterSeconds)));

        Map<String, Object> errorBody = Map.of(
                "error", "Too Many Requests",
                "status", 429,
                "message", "Rate limit exceeded for admin endpoints. Please try again later.",
                "retryAfter", Math.max(0, retryAfterSeconds) + " seconds",
                "timestamp", Instant.now().toEpochMilli()
        );

        String jsonResponse = objectMapper.writeValueAsString(errorBody);
        response.getWriter().write(jsonResponse);
        response.getWriter().flush();
    }

    /**
     * Mask IP address for logging (privacy).
     * Shows only first 2 octets for IPv4.
     */
    private String maskIp(String ip) {
        if (ip == null || ip.isEmpty()) {
            return "***";
        }

        // For IPv4: show first 2 octets
        String[] parts = ip.split("\\.");
        if (parts.length == 4) {
            return parts[0] + "." + parts[1] + ".*.*";
        }

        // For IPv6 or other: show first N characters
        if (ip.length() > ipVisibleChars) {
            return ip.substring(0, ipVisibleChars) + "***";
        }

        return "***";
    }
}
