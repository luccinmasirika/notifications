package com.irembo.notifications.infra.redis;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
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

    /**
     * Calculate the exact reset time for the monthly quota.
     * Returns the first instant of the next month.
     *
     * @return Instant when the monthly counter resets (start of next month)
     */
    public Instant getMonthlyResetTime() {
        YearMonth currentMonth = YearMonth.now(ZoneOffset.UTC);
        YearMonth nextMonth = currentMonth.plusMonths(1);

        // First day of next month at 00:00:00 UTC
        return nextMonth.atDay(1)
                .atStartOfDay()
                .toInstant(ZoneOffset.UTC);
    }

    /**
     * Batch increment all counters using Redis pipelining.
     * This reduces network round trips from 3-4 to 1, improving performance under high load.
     *
     * Performance benefit:
     * - Before: 3-4 Redis calls (window + monthly + global + optional expire) = 3-4 network RTTs
     * - After: 1 Redis pipeline with all operations = 1 network RTT
     * - Latency reduction: ~60-75% (depending on network conditions)
     *
     * @param clientId Client identifier
     * @param windowSeconds Window size in seconds
     * @param hasGlobalLimit Whether to increment global counter
     * @param globalWindowSeconds Global window size (only used if hasGlobalLimit is true)
     * @return BatchIncrementResult containing all incremented values
     */
    public BatchIncrementResult batchIncrementCounters(
            String clientId,
            int windowSeconds,
            boolean hasGlobalLimit,
            int globalWindowSeconds) {

        long now = Instant.now().getEpochSecond();
        long windowStart = (now / windowSeconds) * windowSeconds;
        String yearMonth = getCurrentYearMonth();

        // Prepare keys
        String windowKey = String.format("rate:client:%s:window:%d", clientId, windowStart);
        String monthlyKey = String.format("rate:client:%s:monthly:%s", clientId, yearMonth);
        String globalKey = hasGlobalLimit
                ? String.format("rate:global:window:%d", (now / globalWindowSeconds) * globalWindowSeconds)
                : null;

        // Execute all increments in a single pipeline
        List<Object> results = redisTemplate.executePipelined(new RedisCallback<Object>() {
            @Override
            public Object doInRedis(RedisConnection connection) throws DataAccessException {
                // Use stringCommands() for non-deprecated API
                byte[] windowKeyBytes = windowKey.getBytes();
                byte[] monthlyKeyBytes = monthlyKey.getBytes();

                // Increment window counter
                connection.stringCommands().incr(windowKeyBytes);

                // Increment monthly counter
                connection.stringCommands().incr(monthlyKeyBytes);

                // Increment global counter if needed
                if (hasGlobalLimit && globalKey != null) {
                    connection.stringCommands().incr(globalKey.getBytes());
                }

                return null; // Pipeline callback must return null
            }
        });

        // Parse results
        long windowCount = results.get(0) != null ? ((Number) results.get(0)).longValue() : 0;
        long monthlyCount = results.get(1) != null ? ((Number) results.get(1)).longValue() : 0;
        long globalCount = (hasGlobalLimit && results.size() > 2 && results.get(2) != null)
                ? ((Number) results.get(2)).longValue()
                : 0;

        // Set expiration for first-time counters (not in pipeline to keep it simple)
        // These are rare operations (only on first increment), so the extra RTT is acceptable
        if (windowCount == 1) {
            redisTemplate.expire(windowKey, windowSeconds, TimeUnit.SECONDS);
        }
        if (monthlyCount == 1) {
            YearMonth ym = YearMonth.parse(yearMonth);
            long daysInMonth = ym.lengthOfMonth();
            redisTemplate.expire(monthlyKey, (daysInMonth + 1), TimeUnit.DAYS);
        }
        if (hasGlobalLimit && globalCount == 1 && globalKey != null) {
            redisTemplate.expire(globalKey, globalWindowSeconds, TimeUnit.SECONDS);
        }

        return new BatchIncrementResult(windowCount, monthlyCount, globalCount);
    }

    /**
     * Result object for batch increment operation.
     */
    public record BatchIncrementResult(
            long windowCount,
            long monthlyCount,
            long globalCount
    ) {}
}
