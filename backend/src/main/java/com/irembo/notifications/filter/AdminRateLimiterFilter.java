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

@Component
@Order(0)
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

        if (!path.startsWith("/admin/")) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = getClientIp(request);
        String rateLimitKey = rateLimitKeyPrefix + clientIp;

        long count = redisCounter.incrementWindowCounter(rateLimitKey, windowSeconds);

        response.setHeader("X-RateLimit-Limit", String.valueOf(maxRequests));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(Math.max(0, maxRequests - count)));

        Instant resetTime = redisCounter.getWindowResetTime(windowSeconds);
        response.setHeader("X-RateLimit-Reset", String.valueOf(resetTime.getEpochSecond()));

        if (count > maxRequests) {
            logger.warn("Admin rate limit exceeded for IP: {} (count: {})", maskIp(clientIp), count);
            sendRateLimitExceededResponse(response, resetTime);
            return;
        }

        if (count > (maxRequests * 0.8)) {
            logger.info("Admin IP {} approaching rate limit: {}/{}", maskIp(clientIp), count, maxRequests);
        }

        filterChain.doFilter(request, response);
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

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

    private String maskIp(String ip) {
        if (ip == null || ip.isEmpty()) {
            return "***";
        }

        String[] parts = ip.split("\\.");
        if (parts.length == 4) {
            return parts[0] + "." + parts[1] + ".*.*";
        }

        if (ip.length() > ipVisibleChars) {
            return ip.substring(0, ipVisibleChars) + "***";
        }

        return "***";
    }
}
