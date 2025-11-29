package com.irembo.notifications.service;

import com.irembo.notifications.infra.db.entity.Client;
import com.irembo.notifications.infra.db.entity.ClientLimit;
import com.irembo.notifications.infra.db.repository.ClientLimitRepository;
import com.irembo.notifications.infra.db.repository.ClientRepository;
import com.irembo.notifications.infra.redis.RedisCounterRepository;
import com.irembo.notifications.model.dto.ClientDetailsResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Admin service for managing clients and limits with cache invalidation.
 */
@Service
public class AdminService {

    private static final Logger logger = LoggerFactory.getLogger(AdminService.class);

    private final ClientRepository clientRepository;
    private final ClientLimitRepository clientLimitRepository;
    private final CacheManager cacheManager;
    private final RedisCounterRepository redisCounter;

    private static final double SOFT_THROTTLE_THRESHOLD = 0.80; // 80%
    private static final double HARD_REJECT_THRESHOLD = 1.00;   // 100%

    public AdminService(
            ClientRepository clientRepository,
            ClientLimitRepository clientLimitRepository,
            CacheManager cacheManager,
            RedisCounterRepository redisCounter) {
        this.clientRepository = clientRepository;
        this.clientLimitRepository = clientLimitRepository;
        this.cacheManager = cacheManager;
        this.redisCounter = redisCounter;
    }

    /**
     * Update client and evict cache.
     */
    @Transactional
    public Client updateClient(Long id, Client client) {
        client.setId(id);
        Client updated = clientRepository.save(client);
        evictClientCache(updated.getApiKey());
        logger.info("Updated client {} and evicted cache", id);
        return updated;
    }

    /**
     * Create or update client limit and evict cache.
     */
    @Transactional
    @CacheEvict(value = "clientConfigs", allEntries = true)
    public ClientLimit createOrUpdateClientLimit(Long clientId, ClientLimit limit) {
        limit.setClientId(clientId);

        Optional<ClientLimit> existing = clientLimitRepository.findByClientId(clientId);
        if (existing.isPresent()) {
            limit.setId(existing.get().getId());
        }

        ClientLimit saved = clientLimitRepository.save(limit);

        // Evict cache for this specific client
        Optional<Client> client = clientRepository.findById(clientId);
        client.ifPresent(c -> {
            evictClientCache(c.getApiKey());
            logger.info("Updated limits for client {} (API Key: ***{}), evicted cache",
                    clientId, maskApiKey(c.getApiKey()));
        });

        return saved;
    }

    /**
     * Update client limit directly and evict cache.
     */
    @Transactional
    @CacheEvict(value = "clientConfigs", allEntries = true)
    public ClientLimit updateClientLimit(ClientLimit limit) {
        ClientLimit saved = clientLimitRepository.save(limit);

        // Evict cache for this specific client
        Optional<Client> client = clientRepository.findById(limit.getClientId());
        client.ifPresent(c -> {
            evictClientCache(c.getApiKey());
            logger.info("Updated limits for client {} (API Key: ***{}), evicted cache",
                    limit.getClientId(), maskApiKey(c.getApiKey()));
        });

        return saved;
    }

    /**
     * Delete client limit and evict cache.
     */
    @Transactional
    @CacheEvict(value = "clientConfigs", allEntries = true)
    public void deleteClientLimit(Long id) {
        Optional<ClientLimit> limit = clientLimitRepository.findById(id);
        if (limit.isPresent()) {
            Long clientId = limit.get().getClientId();
            clientLimitRepository.deleteById(id);

            // Evict cache for this specific client
            Optional<Client> client = clientRepository.findById(clientId);
            client.ifPresent(c -> {
                evictClientCache(c.getApiKey());
                logger.info("Deleted limits for client {} (API Key: ***{}), evicted cache",
                        clientId, maskApiKey(c.getApiKey()));
            });
        }
    }

    /**
     * Evict cache entries for a specific client by API key.
     */
    private void evictClientCache(String apiKey) {
        try {
            var cache = cacheManager.getCache("clientConfigs");
            if (cache != null) {
                // Evict all entries since we can't target specific keys easily with Caffeine
                cache.clear();
                logger.debug("Evicted clientConfigs cache for API key: ***{}", maskApiKey(apiKey));
            }
        } catch (Exception e) {
            logger.error("Failed to evict cache for API key: ***{}", maskApiKey(apiKey), e);
        }
    }

    /**
     * Get complete client details including usage statistics, limits, and status.
     */
    public ClientDetailsResponse getClientDetails(Long clientId) {
        Optional<Client> clientOpt = clientRepository.findById(clientId);
        if (clientOpt.isEmpty()) {
            throw new IllegalArgumentException("Client not found: " + clientId);
        }

        Client client = clientOpt.get();
        Optional<ClientLimit> limitOpt = clientLimitRepository.findByClientId(clientId);
        
        if (limitOpt.isEmpty()) {
            // Return client info without limits
            return new ClientDetailsResponse(
                client,
                null,
                null,
                null,
                new ClientDetailsResponse.ClientStatus(
                    client.getActive(),
                    false,
                    false,
                    "No rate limits configured",
                    null
                )
            );
        }

        ClientLimit limit = limitOpt.get();
        String clientIdStr = clientId.toString();
        String yearMonth = redisCounter.getCurrentYearMonth();

        // Window usage
        long windowCount = redisCounter.getWindowCounter(clientIdStr, limit.getWindowSizeSeconds());
        long maxWindowRequests = limit.getMaxRequestsPerWindow();
        double windowUsagePercent = maxWindowRequests > 0 
            ? (double) windowCount / maxWindowRequests * 100 
            : 0.0;
        long windowRemaining = Math.max(0, maxWindowRequests - windowCount);
        Instant windowReset = redisCounter.getWindowResetTime(limit.getWindowSizeSeconds());
        boolean windowSoftThrottled = windowUsagePercent >= (SOFT_THROTTLE_THRESHOLD * 100) && windowUsagePercent < (HARD_REJECT_THRESHOLD * 100);
        boolean windowBlocked = windowUsagePercent >= (HARD_REJECT_THRESHOLD * 100);

        ClientDetailsResponse.WindowUsage windowUsage = new ClientDetailsResponse.WindowUsage(
            windowCount,
            maxWindowRequests,
            limit.getWindowSizeSeconds(),
            windowUsagePercent,
            windowRemaining,
            windowReset,
            windowSoftThrottled,
            windowBlocked
        );

        // Monthly usage
        long monthlyCount = redisCounter.getMonthlyCounter(clientIdStr, yearMonth);
        long monthlyQuota = limit.getMonthlyQuota();
        double monthlyUsagePercent = monthlyQuota > 0 
            ? (double) monthlyCount / monthlyQuota * 100 
            : 0.0;
        long monthlyRemaining = Math.max(0, monthlyQuota - monthlyCount);
        boolean monthlySoftThrottled = monthlyUsagePercent >= (SOFT_THROTTLE_THRESHOLD * 100) && monthlyUsagePercent < (HARD_REJECT_THRESHOLD * 100);
        boolean monthlyBlocked = monthlyUsagePercent >= (HARD_REJECT_THRESHOLD * 100);

        ClientDetailsResponse.MonthlyUsage monthlyUsage = new ClientDetailsResponse.MonthlyUsage(
            monthlyCount,
            monthlyQuota,
            monthlyUsagePercent,
            monthlyRemaining,
            monthlySoftThrottled,
            monthlyBlocked
        );

        // Overall status
        boolean isBlocked = windowBlocked || monthlyBlocked || !client.getActive();
        boolean isSoftThrottled = (windowSoftThrottled || monthlySoftThrottled) && !isBlocked;
        String statusMessage = buildStatusMessage(isBlocked, isSoftThrottled, windowBlocked, monthlyBlocked, !client.getActive());
        Instant nextReset = windowReset; // Window reset is more immediate than monthly

        ClientDetailsResponse.ClientStatus status = new ClientDetailsResponse.ClientStatus(
            client.getActive(),
            isBlocked,
            isSoftThrottled,
            statusMessage,
            nextReset
        );

        return new ClientDetailsResponse(
            client,
            limit,
            windowUsage,
            monthlyUsage,
            status
        );
    }

    private String buildStatusMessage(boolean isBlocked, boolean isSoftThrottled, 
                                     boolean windowBlocked, boolean monthlyBlocked, boolean inactive) {
        if (inactive) {
            return "Client is inactive";
        }
        if (isBlocked) {
            if (windowBlocked && monthlyBlocked) {
                return "Blocked: Window and monthly quota exceeded";
            } else if (windowBlocked) {
                return "Blocked: Window limit exceeded";
            } else if (monthlyBlocked) {
                return "Blocked: Monthly quota exceeded";
            }
        }
        if (isSoftThrottled) {
            return "Soft throttled: Usage between 80-99%";
        }
        return "Active and within limits";
    }

    /**
     * Mask API key for logging (show only last 4 characters).
     */
    private String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() <= 4) {
            return "****";
        }
        return apiKey.substring(apiKey.length() - 4);
    }
}
