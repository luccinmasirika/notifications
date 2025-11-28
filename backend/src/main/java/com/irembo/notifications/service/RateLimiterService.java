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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

@Service
public class RateLimiterService {

    private static final Logger logger = LoggerFactory.getLogger(RateLimiterService.class);

    private static final double SOFT_THROTTLE_THRESHOLD = 0.80; // 80%
    private static final double HARD_REJECT_THRESHOLD = 1.00;   // 100%

    private final RedisCounterRepository redisCounter;
    private final ClientRepository clientRepository;
    private final ClientLimitRepository clientLimitRepository;
    private final SystemLimitRepository systemLimitRepository;

    public RateLimiterService(
            RedisCounterRepository redisCounter,
            ClientRepository clientRepository,
            ClientLimitRepository clientLimitRepository,
            SystemLimitRepository systemLimitRepository) {
        this.redisCounter = redisCounter;
        this.clientRepository = clientRepository;
        this.clientLimitRepository = clientLimitRepository;
        this.systemLimitRepository = systemLimitRepository;
    }

    /**
     * Check rate limits and consume a request quota.
     *
     * @param apiKey API key from request header
     * @param channel Notification channel (e.g., "SMS", "EMAIL")
     * @return RateDecision indicating whether to allow, throttle, or reject
     */
    public RateDecision checkAndConsume(String apiKey, String channel) {
        // 1. Validate client exists and is active
        Optional<Client> clientOpt = findClientByApiKey(apiKey);
        if (clientOpt.isEmpty() || !clientOpt.get().getActive()) {
            logger.warn("Invalid or inactive API key: {}", apiKey);
            return RateDecision.hardReject(0, Instant.now().plusSeconds(60), 100.0);
        }

        Client client = clientOpt.get();

        // 2. Get client limits
        Optional<ClientLimit> clientLimitOpt = findClientLimit(client.getId());
        if (clientLimitOpt.isEmpty()) {
            logger.warn("No rate limit configuration found for client: {}", client.getId());
            return RateDecision.hardReject(0, Instant.now().plusSeconds(60), 100.0);
        }

        ClientLimit clientLimit = clientLimitOpt.get();

        // 3. Check global system limits first
        Optional<SystemLimit> globalLimitOpt = findGlobalLimit();
        if (globalLimitOpt.isPresent()) {
            SystemLimit globalLimit = globalLimitOpt.get();
            RateDecision globalDecision = checkGlobalLimit(globalLimit);
            if (globalDecision.type() == DecisionType.HARD_REJECT) {
                return globalDecision;
            }
        }

        // 4. Check client window limit
        RateDecision windowDecision = checkClientWindowLimit(client.getId().toString(), clientLimit);
        if (windowDecision.type() == DecisionType.HARD_REJECT) {
            return windowDecision;
        }

        // 5. Check client monthly quota
        RateDecision monthlyDecision = checkClientMonthlyQuota(client.getId().toString(), clientLimit);
        if (monthlyDecision.type() == DecisionType.HARD_REJECT) {
            return monthlyDecision;
        }

        // 6. Increment counters (consume the request)
        redisCounter.incrementWindowCounter(client.getId().toString(), clientLimit.getWindowSizeSeconds());
        redisCounter.incrementMonthlyCounter(client.getId().toString(), redisCounter.getCurrentYearMonth());
        if (globalLimitOpt.isPresent()) {
            redisCounter.incrementGlobalWindow(globalLimitOpt.get().getWindowSizeSeconds());
        }

        // 7. Return the most restrictive decision (soft throttle takes precedence over allow)
        if (windowDecision.type() == DecisionType.SOFT_THROTTLE || monthlyDecision.type() == DecisionType.SOFT_THROTTLE) {
            return windowDecision.usagePercent() > monthlyDecision.usagePercent() ? windowDecision : monthlyDecision;
        }

        return windowDecision;
    }

    /**
     * Check global system-wide rate limit.
     */
    private RateDecision checkGlobalLimit(SystemLimit globalLimit) {
        long currentCount = redisCounter.getGlobalWindow(globalLimit.getWindowSizeSeconds());
        long maxRequests = globalLimit.getMaxRequestsPerWindow();
        double usagePercent = (double) currentCount / maxRequests;

        Instant resetTime = redisCounter.getWindowResetTime(globalLimit.getWindowSizeSeconds());

        if (usagePercent >= HARD_REJECT_THRESHOLD) {
            logger.warn("Global rate limit exceeded: {} / {}", currentCount, maxRequests);
            return RateDecision.hardReject(maxRequests, resetTime, usagePercent * 100);
        }

        if (usagePercent >= SOFT_THROTTLE_THRESHOLD) {
            logger.info("Global rate limit soft throttle: {} / {}", currentCount, maxRequests);
            return RateDecision.softThrottle(maxRequests, maxRequests - currentCount, resetTime, usagePercent * 100);
        }

        return RateDecision.allow(maxRequests, maxRequests - currentCount, resetTime, usagePercent * 100);
    }

    /**
     * Check client-specific window-based rate limit.
     */
    private RateDecision checkClientWindowLimit(String clientId, ClientLimit clientLimit) {
        long currentCount = redisCounter.getWindowCounter(clientId, clientLimit.getWindowSizeSeconds());
        long maxRequests = clientLimit.getMaxRequestsPerWindow();
        double usagePercent = (double) currentCount / maxRequests;

        Instant resetTime = redisCounter.getWindowResetTime(clientLimit.getWindowSizeSeconds());

        if (usagePercent >= HARD_REJECT_THRESHOLD) {
            logger.warn("Client {} window limit exceeded: {} / {}", clientId, currentCount, maxRequests);
            return RateDecision.hardReject(maxRequests, resetTime, usagePercent * 100);
        }

        if (usagePercent >= SOFT_THROTTLE_THRESHOLD) {
            logger.info("Client {} window limit soft throttle: {} / {}", clientId, currentCount, maxRequests);
            return RateDecision.softThrottle(maxRequests, maxRequests - currentCount, resetTime, usagePercent * 100);
        }

        return RateDecision.allow(maxRequests, maxRequests - currentCount, resetTime, usagePercent * 100);
    }

    /**
     * Check client-specific monthly quota.
     */
    private RateDecision checkClientMonthlyQuota(String clientId, ClientLimit clientLimit) {
        String yearMonth = redisCounter.getCurrentYearMonth();
        long currentCount = redisCounter.getMonthlyCounter(clientId, yearMonth);
        long monthlyQuota = clientLimit.getMonthlyQuota();
        double usagePercent = (double) currentCount / monthlyQuota;

        // Monthly reset is at the start of next month
        Instant resetTime = Instant.now().plusSeconds(30 * 24 * 3600); // Approximate

        if (usagePercent >= HARD_REJECT_THRESHOLD) {
            logger.warn("Client {} monthly quota exceeded: {} / {}", clientId, currentCount, monthlyQuota);
            return RateDecision.hardReject(monthlyQuota, resetTime, usagePercent * 100);
        }

        if (usagePercent >= SOFT_THROTTLE_THRESHOLD) {
            logger.info("Client {} monthly quota soft throttle: {} / {}", clientId, currentCount, monthlyQuota);
            return RateDecision.softThrottle(monthlyQuota, monthlyQuota - currentCount, resetTime, usagePercent * 100);
        }

        return RateDecision.allow(monthlyQuota, monthlyQuota - currentCount, resetTime, usagePercent * 100);
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

    @Cacheable("clientConfigs")
    private Optional<Client> findClientByApiKey(String apiKey) {
        return clientRepository.findByApiKey(apiKey);
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
