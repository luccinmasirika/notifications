package com.irembo.notifications.infra.redis;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;

@Repository
public class RedisCounterRepository {

    private final RedisTemplate<String, Object> redisTemplate;

    public RedisCounterRepository(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Increment window counter for a client and return the current count.
     * Uses sliding window based on current timestamp.
     *
     * @param clientId Client identifier
     * @param windowSeconds Window size in seconds
     * @return Current count in the window
     */
    public long incrementWindowCounter(String clientId, int windowSeconds) {
        long now = Instant.now().getEpochSecond();
        long windowStart = (now / windowSeconds) * windowSeconds;

        String key = String.format("rate:client:%s:window:%d", clientId, windowStart);

        Long count = redisTemplate.opsForValue().increment(key);

        // Set expiration on first increment
        if (count != null && count == 1) {
            redisTemplate.expire(key, windowSeconds, TimeUnit.SECONDS);
        }

        return count != null ? count : 0;
    }

    /**
     * Get current window counter without incrementing.
     *
     * @param clientId Client identifier
     * @param windowSeconds Window size in seconds
     * @return Current count in the window
     */
    public long getWindowCounter(String clientId, int windowSeconds) {
        long now = Instant.now().getEpochSecond();
        long windowStart = (now / windowSeconds) * windowSeconds;

        String key = String.format("rate:client:%s:window:%d", clientId, windowStart);

        Object value = redisTemplate.opsForValue().get(key);
        return value != null ? Long.parseLong(value.toString()) : 0;
    }

    /**
     * Increment monthly counter for a client and return the current count.
     *
     * @param clientId Client identifier
     * @param yearMonth Year-Month identifier (e.g., "2025-01")
     * @return Current count for the month
     */
    public long incrementMonthlyCounter(String clientId, String yearMonth) {
        String key = String.format("rate:client:%s:monthly:%s", clientId, yearMonth);

        Long count = redisTemplate.opsForValue().increment(key);

        // Set expiration to end of month + 1 day
        if (count != null && count == 1) {
            YearMonth ym = YearMonth.parse(yearMonth);
            long daysInMonth = ym.lengthOfMonth();
            redisTemplate.expire(key, (daysInMonth + 1), TimeUnit.DAYS);
        }

        return count != null ? count : 0;
    }

    /**
     * Get current monthly counter without incrementing.
     *
     * @param clientId Client identifier
     * @param yearMonth Year-Month identifier (e.g., "2025-01")
     * @return Current count for the month
     */
    public long getMonthlyCounter(String clientId, String yearMonth) {
        String key = String.format("rate:client:%s:monthly:%s", clientId, yearMonth);

        Object value = redisTemplate.opsForValue().get(key);
        return value != null ? Long.parseLong(value.toString()) : 0;
    }

    /**
     * Increment global window counter and return the current count.
     *
     * @param windowSeconds Window size in seconds
     * @return Current global count in the window
     */
    public long incrementGlobalWindow(int windowSeconds) {
        long now = Instant.now().getEpochSecond();
        long windowStart = (now / windowSeconds) * windowSeconds;

        String key = String.format("rate:global:window:%d", windowStart);

        Long count = redisTemplate.opsForValue().increment(key);

        // Set expiration on first increment
        if (count != null && count == 1) {
            redisTemplate.expire(key, windowSeconds, TimeUnit.SECONDS);
        }

        return count != null ? count : 0;
    }

    /**
     * Get current global window counter without incrementing.
     *
     * @param windowSeconds Window size in seconds
     * @return Current global count in the window
     */
    public long getGlobalWindow(int windowSeconds) {
        long now = Instant.now().getEpochSecond();
        long windowStart = (now / windowSeconds) * windowSeconds;

        String key = String.format("rate:global:window:%d", windowStart);

        Object value = redisTemplate.opsForValue().get(key);
        return value != null ? Long.parseLong(value.toString()) : 0;
    }

    /**
     * Calculate when the current window will reset.
     *
     * @param windowSeconds Window size in seconds
     * @return Instant when the window resets
     */
    public Instant getWindowResetTime(int windowSeconds) {
        long now = Instant.now().getEpochSecond();
        long windowStart = (now / windowSeconds) * windowSeconds;
        long windowEnd = windowStart + windowSeconds;

        return Instant.ofEpochSecond(windowEnd);
    }

    /**
     * Get current year-month string for monthly quotas.
     *
     * @return Year-Month string (e.g., "2025-01")
     */
    public String getCurrentYearMonth() {
        return YearMonth.now(ZoneOffset.UTC).toString();
    }
}
