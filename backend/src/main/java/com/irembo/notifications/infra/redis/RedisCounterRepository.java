package com.irembo.notifications.infra.redis;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Repository
public class RedisCounterRepository {

    private final RedisTemplate<String, Object> redisTemplate;
    
    private static final String ATOMIC_CHECK_INCREMENT_SCRIPT = 
        "local current = redis.call('GET', KEYS[1])\n" +
        "if current == false then\n" +
        "    current = 0\n" +
        "else\n" +
        "    current = tonumber(current)\n" +
        "end\n" +
        "local limit = tonumber(ARGV[1])\n" +
        "local hardThreshold = tonumber(ARGV[2])\n" +
        "local softThreshold = tonumber(ARGV[3])\n" +
        "local expireSeconds = tonumber(ARGV[4])\n" +
        "local futureCount = current + 1\n" +
        "local usagePercent = futureCount / limit\n" +
        "local decision = 0\n" +
        "if usagePercent >= hardThreshold then\n" +
        "    decision = 2\n" +
        "elseif usagePercent >= softThreshold then\n" +
        "    decision = 1\n" +
        "else\n" +
        "    decision = 0\n" +
        "end\n" +
        "if decision < 2 then\n" +
        "    local newCount = redis.call('INCR', KEYS[1])\n" +
        "    if newCount == 1 and expireSeconds > 0 then\n" +
        "        redis.call('EXPIRE', KEYS[1], expireSeconds)\n" +
        "    end\n" +
        "    return {decision, newCount, usagePercent * 100}\n" +
        "else\n" +
        "    return {decision, current, usagePercent * 100}\n" +
        "end";

    public RedisCounterRepository(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public long incrementWindowCounter(String clientId, int windowSeconds) {
        long now = Instant.now().getEpochSecond();
        long windowStart = (now / windowSeconds) * windowSeconds;

        String key = String.format("rate:client:%s:window:%d", clientId, windowStart);

        Long count = redisTemplate.opsForValue().increment(key);

        if (count != null && count == 1) {
            redisTemplate.expire(key, windowSeconds, TimeUnit.SECONDS);
        }

        return count != null ? count : 0;
    }

    public long getWindowCounter(String clientId, int windowSeconds) {
        long now = Instant.now().getEpochSecond();
        long windowStart = (now / windowSeconds) * windowSeconds;

        String key = String.format("rate:client:%s:window:%d", clientId, windowStart);

        Object value = redisTemplate.opsForValue().get(key);
        return value != null ? Long.parseLong(value.toString()) : 0;
    }

    public long incrementMonthlyCounter(String clientId, String yearMonth) {
        String key = String.format("rate:client:%s:monthly:%s", clientId, yearMonth);

        Long count = redisTemplate.opsForValue().increment(key);

        if (count != null && count == 1) {
            YearMonth ym = YearMonth.parse(yearMonth);
            long daysInMonth = ym.lengthOfMonth();
            redisTemplate.expire(key, (daysInMonth + 1), TimeUnit.DAYS);
        }

        return count != null ? count : 0;
    }

    public long getMonthlyCounter(String clientId, String yearMonth) {
        String key = String.format("rate:client:%s:monthly:%s", clientId, yearMonth);

        Object value = redisTemplate.opsForValue().get(key);
        return value != null ? Long.parseLong(value.toString()) : 0;
    }

    public long incrementGlobalWindow(int windowSeconds) {
        long now = Instant.now().getEpochSecond();
        long windowStart = (now / windowSeconds) * windowSeconds;

        String key = String.format("rate:global:window:%d", windowStart);

        Long count = redisTemplate.opsForValue().increment(key);

        if (count != null && count == 1) {
            redisTemplate.expire(key, windowSeconds, TimeUnit.SECONDS);
        }

        return count != null ? count : 0;
    }

    public long getGlobalWindow(int windowSeconds) {
        long now = Instant.now().getEpochSecond();
        long windowStart = (now / windowSeconds) * windowSeconds;

        String key = String.format("rate:global:window:%d", windowStart);

        Object value = redisTemplate.opsForValue().get(key);
        return value != null ? Long.parseLong(value.toString()) : 0;
    }

    public Instant getWindowResetTime(int windowSeconds) {
        long now = Instant.now().getEpochSecond();
        long windowStart = (now / windowSeconds) * windowSeconds;
        long windowEnd = windowStart + windowSeconds;

        return Instant.ofEpochSecond(windowEnd);
    }

    public String getCurrentYearMonth() {
        return YearMonth.now(ZoneOffset.UTC).toString();
    }

    public Instant getMonthlyResetTime() {
        YearMonth currentMonth = YearMonth.now(ZoneOffset.UTC);
        YearMonth nextMonth = currentMonth.plusMonths(1);

        return nextMonth.atDay(1)
                .atStartOfDay()
                .toInstant(ZoneOffset.UTC);
    }

    public BatchIncrementResult batchIncrementCounters(
            String clientId,
            int windowSeconds,
            boolean hasGlobalLimit,
            int globalWindowSeconds) {

        long now = Instant.now().getEpochSecond();
        long windowStart = (now / windowSeconds) * windowSeconds;
        String yearMonth = getCurrentYearMonth();

        String windowKey = String.format("rate:client:%s:window:%d", clientId, windowStart);
        String monthlyKey = String.format("rate:client:%s:monthly:%s", clientId, yearMonth);
        String globalKey = hasGlobalLimit
                ? String.format("rate:global:window:%d", (now / globalWindowSeconds) * globalWindowSeconds)
                : null;

        List<Object> results = redisTemplate.executePipelined(new RedisCallback<Object>() {
            @Override
            public Object doInRedis(RedisConnection connection) throws DataAccessException {
                byte[] windowKeyBytes = windowKey.getBytes();
                byte[] monthlyKeyBytes = monthlyKey.getBytes();

                connection.stringCommands().incr(windowKeyBytes);

                connection.stringCommands().incr(monthlyKeyBytes);

                if (hasGlobalLimit && globalKey != null) {
                    connection.stringCommands().incr(globalKey.getBytes());
                }

                return null;
            }
        });

        long windowCount = results.get(0) != null ? ((Number) results.get(0)).longValue() : 0;
        long monthlyCount = results.get(1) != null ? ((Number) results.get(1)).longValue() : 0;
        long globalCount = (hasGlobalLimit && results.size() > 2 && results.get(2) != null)
                ? ((Number) results.get(2)).longValue()
                : 0;

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

    public record BatchIncrementResult(
            long windowCount,
            long monthlyCount,
            long globalCount
    ) {}
    
    public record AtomicCheckResult(
            boolean allowed,
            long count,
            double usagePercent,
            int decision
    ) {}
    
    public AtomicCheckResult atomicCheckAndIncrement(
            String key,
            long limit,
            double hardRejectThreshold,
            double softThrottleThreshold,
            int expireSeconds) {
        
        @SuppressWarnings("rawtypes")
        DefaultRedisScript script = new DefaultRedisScript<>();
        script.setScriptText(ATOMIC_CHECK_INCREMENT_SCRIPT);
        script.setResultType(List.class);
        
        @SuppressWarnings("unchecked")
        List<Long> result = (List<Long>) redisTemplate.execute(script,
                Arrays.asList(key),
                String.valueOf(limit),
                String.valueOf(hardRejectThreshold),
                String.valueOf(softThrottleThreshold),
                String.valueOf(expireSeconds));
        
        if (result == null || result.size() < 3) {
            return new AtomicCheckResult(false, 0, 100.0, 2);
        }
        
        int decision = result.get(0).intValue();
        long count = result.get(1).longValue();
        double usagePercent = result.get(2).doubleValue();
        boolean allowed = decision < 2;
        
        return new AtomicCheckResult(allowed, count, usagePercent, decision);
    }
    
    public AtomicBatchCheckResult atomicCheckAndIncrementBatch(
            String clientId,
            String windowKey,
            long windowLimit,
            double windowHardThreshold,
            double windowSoftThreshold,
            int windowExpireSeconds,
            String monthlyKey,
            long monthlyLimit,
            double monthlyHardThreshold,
            double monthlySoftThreshold,
            int monthlyExpireDays,
            String globalKey,
            Long globalLimit,
            int globalExpireSeconds) {
        
        AtomicCheckResult windowResult = atomicCheckAndIncrement(
                windowKey, windowLimit, windowHardThreshold, windowSoftThreshold, windowExpireSeconds);
        
        if (windowResult.decision() == 2) {
            return new AtomicBatchCheckResult(
                    windowResult, null, null, false);
        }
        
        AtomicCheckResult monthlyResult = atomicCheckAndIncrement(
                monthlyKey, monthlyLimit, monthlyHardThreshold, monthlySoftThreshold,
                monthlyExpireDays * 24 * 3600);
        
        if (monthlyResult.decision() == 2) {
            return new AtomicBatchCheckResult(
                    windowResult, monthlyResult, null, false);
        }
        
        AtomicCheckResult globalResult = null;
        if (globalKey != null && globalLimit != null) {
            globalResult = atomicCheckAndIncrement(
                    globalKey, globalLimit, 1.0, 0.8, globalExpireSeconds);
            
            if (globalResult.decision() == 2) {
                return new AtomicBatchCheckResult(
                        windowResult, monthlyResult, globalResult, false);
            }
        }
        
        return new AtomicBatchCheckResult(
                windowResult, monthlyResult, globalResult, true);
    }
    
    public record AtomicBatchCheckResult(
            AtomicCheckResult windowResult,
            AtomicCheckResult monthlyResult,
            AtomicCheckResult globalResult,
            boolean allAllowed
    ) {
        public int getMostRestrictiveDecision() {
            int maxDecision = 0;
            if (windowResult != null && windowResult.decision() > maxDecision) {
                maxDecision = windowResult.decision();
            }
            if (monthlyResult != null && monthlyResult.decision() > maxDecision) {
                maxDecision = monthlyResult.decision();
            }
            if (globalResult != null && globalResult.decision() > maxDecision) {
                maxDecision = globalResult.decision();
            }
            return maxDecision;
        }
        
        public double getMaxUsagePercent() {
            double maxUsage = 0.0;
            if (windowResult != null && windowResult.usagePercent() > maxUsage) {
                maxUsage = windowResult.usagePercent();
            }
            if (monthlyResult != null && monthlyResult.usagePercent() > maxUsage) {
                maxUsage = monthlyResult.usagePercent();
            }
            if (globalResult != null && globalResult.usagePercent() > maxUsage) {
                maxUsage = globalResult.usagePercent();
            }
            return maxUsage;
        }
    }
}
