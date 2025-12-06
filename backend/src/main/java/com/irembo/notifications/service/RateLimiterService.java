package com.irembo.notifications.service;

import com.irembo.notifications.infra.db.entity.Client;
import com.irembo.notifications.infra.db.entity.ClientLimit;
import com.irembo.notifications.infra.db.entity.SystemLimit;
import com.irembo.notifications.infra.db.repository.ClientLimitRepository;
import com.irembo.notifications.infra.db.repository.ClientRepository;
import com.irembo.notifications.infra.db.repository.SystemLimitRepository;
import com.irembo.notifications.infra.redis.RedisCounterRepository;
import com.irembo.notifications.model.dto.RateDecision;
import com.irembo.notifications.model.dto.UsageInfo;
import com.irembo.notifications.model.enums.DecisionType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.YearMonth;
import java.util.Optional;

@Service
public class RateLimiterService {

    private static final Logger logger = LoggerFactory.getLogger(RateLimiterService.class);

    // Default thresholds (used as fallback if not configured)
    private static final double DEFAULT_SOFT_THROTTLE_THRESHOLD = 0.80; // 80%
    private static final double DEFAULT_HARD_REJECT_THRESHOLD = 1.00;   // 100%

    private final RedisCounterRepository redisCounter;
    private final ClientRepository clientRepository;
    private final ClientLimitRepository clientLimitRepository;
    private final SystemLimitRepository systemLimitRepository;
    private final AdminService adminService;

    // Metrics
    private final Counter softThrottleCounter;
    private final Counter hardRejectCounter;
    private final Counter invalidApiKeyCounter;
    private final Timer checkAndConsumeTimer;

    public RateLimiterService(
            RedisCounterRepository redisCounter,
            ClientRepository clientRepository,
            ClientLimitRepository clientLimitRepository,
            SystemLimitRepository systemLimitRepository,
            AdminService adminService,
            MeterRegistry meterRegistry) {
        this.redisCounter = redisCounter;
        this.clientRepository = clientRepository;
        this.clientLimitRepository = clientLimitRepository;
        this.systemLimitRepository = systemLimitRepository;
        this.adminService = adminService;

        // Initialize metrics counters
        this.softThrottleCounter = Counter.builder("ratelimiter.soft_throttle")
                .description("Number of requests that were soft throttled")
                .tag("component", "ratelimiter")
                .register(meterRegistry);
        this.hardRejectCounter = Counter.builder("ratelimiter.hard_reject")
                .description("Number of requests that were hard rejected")
                .tag("component", "ratelimiter")
                .register(meterRegistry);
        this.invalidApiKeyCounter = Counter.builder("ratelimiter.invalid_api_key")
                .description("Number of requests with invalid API keys")
                .tag("component", "ratelimiter")
                .register(meterRegistry);

        // Initialize timer for performance monitoring
        this.checkAndConsumeTimer = Timer.builder("ratelimiter.check_and_consume")
                .description("Time taken to check and consume rate limits")
                .tag("component", "ratelimiter")
                .register(meterRegistry);
    }

    /**
     * Check rate limits and consume a request quota.
     * Performance is monitored via the checkAndConsumeTimer metric.
     *
     * @param apiKey API key from request header
     * @param channel Notification channel (e.g., "SMS", "EMAIL")
     * @return RateDecision indicating whether to allow, throttle, or reject
     */
    public RateDecision checkAndConsume(String apiKey, String channel) {
        return checkAndConsumeTimer.record(() -> executeCheckAndConsume(apiKey, channel));
    }

    /**
     * Internal implementation of rate limit check.
     * Uses atomic Redis operations to eliminate race conditions in distributed environments.
     * Wrapped by checkAndConsume() for metrics.
     */
    private RateDecision executeCheckAndConsume(String apiKey, String channel) {
        // 1. Validate client exists and is active
        Optional<Client> clientOpt = findClientByApiKey(apiKey);
        if (clientOpt.isEmpty() || !clientOpt.get().getActive()) {
            logger.warn("Invalid or inactive API key: {}", apiKey);
            invalidApiKeyCounter.increment();
            hardRejectCounter.increment();
            return RateDecision.hardReject(0, Instant.now().plusSeconds(60), 100.0);
        }

        Client client = clientOpt.get();

        // 2. Get client limits
        Optional<ClientLimit> clientLimitOpt = findClientLimit(client.getId());
        if (clientLimitOpt.isEmpty()) {
            logger.warn("No rate limit configuration found for client: {}", client.getId());
            hardRejectCounter.increment();
            return RateDecision.hardReject(0, Instant.now().plusSeconds(60), 100.0);
        }

        ClientLimit clientLimit = clientLimitOpt.get();

        // 3. Get global system limits if configured
        Optional<SystemLimit> globalLimitOpt = findGlobalLimit();

        // 4. Prepare keys and thresholds for atomic operation
        long now = Instant.now().getEpochSecond();
        long windowStart = (now / clientLimit.getWindowSizeSeconds()) * clientLimit.getWindowSizeSeconds();
        String windowKey = String.format("rate:client:%s:window:%d", client.getId(), windowStart);
        
        String yearMonth = redisCounter.getCurrentYearMonth();
        String monthlyKey = String.format("rate:client:%s:monthly:%s", client.getId(), yearMonth);
        
        String globalKey = null;
        Long globalLimit = null;
        int globalWindowSize = 0;
        if (globalLimitOpt.isPresent()) {
            SystemLimit globalLimitEntity = globalLimitOpt.get();
            globalWindowSize = globalLimitEntity.getWindowSizeSeconds();
            long globalWindowStart = (now / globalWindowSize) * globalWindowSize;
            globalKey = String.format("rate:global:window:%d", globalWindowStart);
            globalLimit = Long.valueOf(globalLimitEntity.getMaxRequestsPerWindow());
        }

        // Get thresholds
        double windowHardThreshold = clientLimit.getHardRejectThreshold() != null
                ? clientLimit.getHardRejectThreshold() : DEFAULT_HARD_REJECT_THRESHOLD;
        double windowSoftThreshold = clientLimit.getSoftThrottleThreshold() != null
                ? clientLimit.getSoftThrottleThreshold() : DEFAULT_SOFT_THROTTLE_THRESHOLD;
        double monthlyHardThreshold = windowHardThreshold; // Same thresholds for monthly
        double monthlySoftThreshold = windowSoftThreshold;

        // Calculate expiration times
        YearMonth ym = YearMonth.parse(yearMonth);
        int monthlyExpireDays = ym.lengthOfMonth() + 1;

        // 5. Perform atomic check and increment for all limits
        // This eliminates race conditions in distributed environments
        var batchResult = redisCounter.atomicCheckAndIncrementBatch(
            client.getId().toString(),
                windowKey,
                clientLimit.getMaxRequestsPerWindow(),
                windowHardThreshold,
                windowSoftThreshold,
            clientLimit.getWindowSizeSeconds(),
                monthlyKey,
                clientLimit.getMonthlyQuota(),
                monthlyHardThreshold,
                monthlySoftThreshold,
                monthlyExpireDays,
                globalKey,
                globalLimit,
            globalWindowSize
        );

        // 6. Determine the final decision based on atomic results
        int mostRestrictiveDecision = batchResult.getMostRestrictiveDecision();
        double maxUsagePercent = batchResult.getMaxUsagePercent();

        // Get reset times
        Instant windowReset = redisCounter.getWindowResetTime(clientLimit.getWindowSizeSeconds());
        Instant monthlyReset = redisCounter.getMonthlyResetTime();

        // Build rate decision based on the most restrictive limit
        if (mostRestrictiveDecision == 2) { // HARD_REJECT
            // Find which limit caused the rejection
            var windowResult = batchResult.windowResult();
            var monthlyResult = batchResult.monthlyResult();
            var globalResult = batchResult.globalResult();

            long limit = clientLimit.getMaxRequestsPerWindow();
            Instant resetTime = windowReset;
            
            if (globalResult != null && globalResult.decision() == 2 && globalLimit != null) {
                limit = globalLimit;
                resetTime = redisCounter.getWindowResetTime(globalWindowSize);
                logger.warn("Global rate limit hard reject: usage: {}%", String.format("%.2f", maxUsagePercent));
            } else if (monthlyResult != null && monthlyResult.decision() == 2) {
                limit = clientLimit.getMonthlyQuota();
                resetTime = monthlyReset;
                logger.warn("Client {} monthly quota hard reject: usage: {}%", 
                        client.getId(), String.format("%.2f", maxUsagePercent));
            } else if (windowResult != null && windowResult.decision() == 2) {
                logger.warn("Client {} window limit hard reject: usage: {}%", 
                        client.getId(), String.format("%.2f", maxUsagePercent));
            }

            hardRejectCounter.increment();
            return RateDecision.hardReject(limit, resetTime, maxUsagePercent);
        }

        // Calculate remaining and determine if soft throttle
        var windowResult = batchResult.windowResult();
        var monthlyResult = batchResult.monthlyResult();
        
        long windowRemaining = windowResult != null 
                ? Math.max(0, clientLimit.getMaxRequestsPerWindow() - windowResult.count()) : 0;
        long monthlyRemaining = monthlyResult != null
                ? Math.max(0, clientLimit.getMonthlyQuota() - monthlyResult.count()) : 0;
        long remaining = Math.min(windowRemaining, monthlyRemaining);

        if (mostRestrictiveDecision == 1) { // SOFT_THROTTLE
            softThrottleCounter.increment();
            logger.info("Rate limit soft throttle: usage: {}%", String.format("%.2f", maxUsagePercent));
            return RateDecision.softThrottle(
                    clientLimit.getMaxRequestsPerWindow(), remaining, windowReset, maxUsagePercent);
        }

        // ALLOW
        return RateDecision.allow(
                clientLimit.getMaxRequestsPerWindow(), remaining, windowReset, maxUsagePercent);
    }

    /**
     * Check global system-wide rate limit.
     * Note: Global limits use default thresholds (not configurable per-client).
     * Note: We check if currentCount + 1 would exceed the threshold, since we'll increment after this check.
     */
    private RateDecision checkGlobalLimit(SystemLimit globalLimit) {
        long currentCount = redisCounter.getGlobalWindow(globalLimit.getWindowSizeSeconds());
        long maxRequests = globalLimit.getMaxRequestsPerWindow();
        // Check usage after the increment that will happen
        long futureCount = currentCount + 1;
        double usagePercent = (double) futureCount / maxRequests;

        Instant resetTime = redisCounter.getWindowResetTime(globalLimit.getWindowSizeSeconds());

        if (usagePercent >= DEFAULT_HARD_REJECT_THRESHOLD) {
            logger.warn("Global rate limit exceeded: {} / {} (future count: {})", currentCount, maxRequests, futureCount);
            hardRejectCounter.increment();
            return RateDecision.hardReject(maxRequests, resetTime, usagePercent * 100);
        }

        if (usagePercent >= DEFAULT_SOFT_THROTTLE_THRESHOLD) {
            logger.info("Global rate limit soft throttle: {} / {} (future count: {})", currentCount, maxRequests, futureCount);
            softThrottleCounter.increment();
            return RateDecision.softThrottle(maxRequests, maxRequests - futureCount, resetTime, usagePercent * 100);
        }

        return RateDecision.allow(maxRequests, maxRequests - futureCount, resetTime, usagePercent * 100);
    }

    /**
     * Check client-specific window-based rate limit.
     * Uses configurable thresholds from ClientLimit entity.
     * Note: We check if currentCount + 1 would exceed the threshold, since we'll increment after this check.
     */
    private RateDecision checkClientWindowLimit(String clientId, ClientLimit clientLimit) {
        long currentCount = redisCounter.getWindowCounter(clientId, clientLimit.getWindowSizeSeconds());
        long maxRequests = clientLimit.getMaxRequestsPerWindow();
        // Check usage after the increment that will happen
        long futureCount = currentCount + 1;
        double usagePercent = (double) futureCount / maxRequests;

        Instant resetTime = redisCounter.getWindowResetTime(clientLimit.getWindowSizeSeconds());

        // Use client-specific thresholds (with defaults if not set)
        double hardRejectThreshold = clientLimit.getHardRejectThreshold() != null
            ? clientLimit.getHardRejectThreshold()
            : DEFAULT_HARD_REJECT_THRESHOLD;
        double softThrottleThreshold = clientLimit.getSoftThrottleThreshold() != null
            ? clientLimit.getSoftThrottleThreshold()
            : DEFAULT_SOFT_THROTTLE_THRESHOLD;

        if (usagePercent >= hardRejectThreshold) {
            logger.warn("Client {} window limit exceeded: {} / {} (threshold: {}%, future count: {})",
                clientId, currentCount, maxRequests, hardRejectThreshold * 100, futureCount);
            hardRejectCounter.increment();
            return RateDecision.hardReject(maxRequests, resetTime, usagePercent * 100);
        }

        if (usagePercent >= softThrottleThreshold) {
            logger.info("Client {} window limit soft throttle: {} / {} (threshold: {}%, future count: {})",
                clientId, currentCount, maxRequests, softThrottleThreshold * 100, futureCount);
            softThrottleCounter.increment();
            return RateDecision.softThrottle(maxRequests, maxRequests - futureCount, resetTime, usagePercent * 100);
        }

        return RateDecision.allow(maxRequests, maxRequests - futureCount, resetTime, usagePercent * 100);
    }

    /**
     * Check client-specific monthly quota.
     * Uses configurable thresholds from ClientLimit entity.
     * Note: We check if currentCount + 1 would exceed the threshold, since we'll increment after this check.
     */
    private RateDecision checkClientMonthlyQuota(String clientId, ClientLimit clientLimit) {
        String yearMonth = redisCounter.getCurrentYearMonth();
        long currentCount = redisCounter.getMonthlyCounter(clientId, yearMonth);
        long monthlyQuota = clientLimit.getMonthlyQuota();
        // Check usage after the increment that will happen
        long futureCount = currentCount + 1;
        double usagePercent = (double) futureCount / monthlyQuota;

        // Monthly reset is at the start of next month (exact calculation)
        Instant resetTime = redisCounter.getMonthlyResetTime();

        // Use client-specific thresholds (with defaults if not set)
        double hardRejectThreshold = clientLimit.getHardRejectThreshold() != null
            ? clientLimit.getHardRejectThreshold()
            : DEFAULT_HARD_REJECT_THRESHOLD;
        double softThrottleThreshold = clientLimit.getSoftThrottleThreshold() != null
            ? clientLimit.getSoftThrottleThreshold()
            : DEFAULT_SOFT_THROTTLE_THRESHOLD;

        if (usagePercent >= hardRejectThreshold) {
            logger.warn("Client {} monthly quota exceeded: {} / {} (threshold: {}%, future count: {})",
                clientId, currentCount, monthlyQuota, hardRejectThreshold * 100, futureCount);
            hardRejectCounter.increment();
            return RateDecision.hardReject(monthlyQuota, resetTime, usagePercent * 100);
        }

        if (usagePercent >= softThrottleThreshold) {
            logger.info("Client {} monthly quota soft throttle: {} / {} (threshold: {}%, future count: {})",
                clientId, currentCount, monthlyQuota, softThrottleThreshold * 100, futureCount);
            softThrottleCounter.increment();
            return RateDecision.softThrottle(monthlyQuota, monthlyQuota - futureCount, resetTime, usagePercent * 100);
        }

        return RateDecision.allow(monthlyQuota, monthlyQuota - futureCount, resetTime, usagePercent * 100);
    }

    /**
     * Get usage information for a client.
     */
    public UsageInfo getUsageInfo(String apiKey) {
        Optional<Client> clientOpt = findClientByApiKey(apiKey);
        if (clientOpt.isEmpty()) {
            return new UsageInfo(0.0, 0.0);
        }

        Client client = clientOpt.get();
        Optional<ClientLimit> clientLimitOpt = findClientLimit(client.getId());
        if (clientLimitOpt.isEmpty()) {
            return new UsageInfo(0.0, 0.0);
        }

        ClientLimit clientLimit = clientLimitOpt.get();
        long windowCount = redisCounter.getWindowCounter(client.getId().toString(), clientLimit.getWindowSizeSeconds());
        long monthlyCount = redisCounter.getMonthlyCounter(client.getId().toString(), redisCounter.getCurrentYearMonth());

        double windowUsage = (double) windowCount / clientLimit.getMaxRequestsPerWindow() * 100;
        double monthlyUsage = (double) monthlyCount / clientLimit.getMonthlyQuota() * 100;

        return new UsageInfo(windowUsage, monthlyUsage);
    }

    /**
     * Find client by API key (plain text).
     * Uses AdminService to validate the API key against stored hashes.
     */
    private Optional<Client> findClientByApiKey(String apiKey) {
        // Use AdminService to validate plain text API key against stored hashes
        return adminService.validateApiKey(apiKey);
    }

    @Cacheable("clientConfigs")
    private Optional<ClientLimit> findClientLimit(Long clientId) {
        return clientLimitRepository.findByClientId(clientId);
    }

    @Cacheable("systemLimits")
    private Optional<SystemLimit> findGlobalLimit() {
        return systemLimitRepository.findByNameAndActiveTrue("global_rate_limit");
    }
}
