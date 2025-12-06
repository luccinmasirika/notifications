package com.irembo.notifications.service;

import com.irembo.notifications.infra.db.entity.Client;
import com.irembo.notifications.infra.db.entity.ClientLimit;
import com.irembo.notifications.infra.db.repository.ClientLimitRepository;
import com.irembo.notifications.infra.db.repository.ClientRepository;
import com.irembo.notifications.infra.redis.RedisCounterRepository;
import com.irembo.notifications.model.dto.ClientDetailsResponse;
import com.irembo.notifications.model.dto.ClientDto;
import com.irembo.notifications.model.dto.CreateClientResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Service
public class AdminService {

    private static final Logger logger = LoggerFactory.getLogger(AdminService.class);

    @Value("${app.default-client-limits.window-size-seconds:60}")
    private int defaultWindowSizeSeconds;

    @Value("${app.default-client-limits.max-requests-per-window:100}")
    private int defaultMaxRequestsPerWindow;

    @Value("${app.default-client-limits.monthly-quota:10000}")
    private int defaultMonthlyQuota;

    @Value("${app.default-client-limits.soft-throttle-threshold:0.80}")
    private double defaultSoftThrottleThreshold;

    @Value("${app.default-client-limits.hard-reject-threshold:1.00}")
    private double defaultHardRejectThreshold;

    @Value("${app.api-secret.secret-bytes:64}")
    private int apiSecretBytes;

    private final ClientRepository clientRepository;
    private final ClientLimitRepository clientLimitRepository;
    private final CacheManager cacheManager;
    private final RedisCounterRepository redisCounter;
    private final ApiKeyHashService apiKeyHashService;
    private final ApiKeyValidationService apiKeyValidationService;
    private final CryptoService cryptoService;
    private final ApiKeyGeneratorService apiKeyGeneratorService;

    public AdminService(
            ClientRepository clientRepository,
            ClientLimitRepository clientLimitRepository,
            CacheManager cacheManager,
            RedisCounterRepository redisCounter,
            ApiKeyHashService apiKeyHashService,
            ApiKeyValidationService apiKeyValidationService,
            CryptoService cryptoService,
            ApiKeyGeneratorService apiKeyGeneratorService) {
        this.clientRepository = clientRepository;
        this.clientLimitRepository = clientLimitRepository;
        this.cacheManager = cacheManager;
        this.redisCounter = redisCounter;
        this.apiKeyHashService = apiKeyHashService;
        this.apiKeyValidationService = apiKeyValidationService;
        this.cryptoService = cryptoService;
        this.apiKeyGeneratorService = apiKeyGeneratorService;
    }

    @Transactional
    @CacheEvict(value = {"apiKeyValidation", "clientConfigs"}, allEntries = true)
    public Client updateClient(Long id, Client client) {
        client.setId(id);
        Client updated = clientRepository.save(client);
        evictClientCache(updated.getApiKeyHash());
        logger.info("Updated client {} and evicted cache (including API key validation cache)", id);
        return updated;
    }

    @Transactional
    @CacheEvict(value = {"apiKeyValidation", "clientConfigs"}, allEntries = true)
    public Client updateClientApiKey(Long clientId, String newApiKey) {
        Optional<Client> clientOpt = clientRepository.findById(clientId);
        if (clientOpt.isEmpty()) {
            throw new IllegalArgumentException("Client not found: " + clientId);
        }

        Client client = clientOpt.get();
        
        String[] hashAndSalt = apiKeyValidationService.hashApiKeyForStorage(
            newApiKey, 
            client.getClientSalt()
        );
        String hashedKey = hashAndSalt[0];
        String clientSalt = hashAndSalt[1];
        String apiKeyIndex = apiKeyHashService.calculateApiKeyIndex(newApiKey);

        client.setApiKeyHash(hashedKey);
        client.setApiKeyIndex(apiKeyIndex);
        client.setClientSalt(clientSalt);

        Client updated = clientRepository.save(client);
        evictClientCache(updated.getApiKeyHash());
        logger.info("Updated API key for client {} using SHA-256 validation", clientId);
        return updated;
    }

    @Transactional
    @CacheEvict(value = "clientConfigs", allEntries = true)
    public ClientLimit createOrUpdateClientLimit(Long clientId, ClientLimit limit) {
        limit.setClientId(clientId);

        Optional<ClientLimit> existing = clientLimitRepository.findByClientId(clientId);
        if (existing.isPresent()) {
            limit.setId(existing.get().getId());
        }

        ClientLimit saved = clientLimitRepository.save(limit);

        Optional<Client> client = clientRepository.findById(clientId);
        client.ifPresent(c -> {
            evictClientCache(c.getApiKeyHash());
            logger.info("Updated limits for client {}, evicted cache", clientId);
        });

        return saved;
    }

    @Transactional
    @CacheEvict(value = "clientConfigs", allEntries = true)
    public ClientLimit updateClientLimit(ClientLimit limit) {
        ClientLimit saved = clientLimitRepository.save(limit);

        Optional<Client> client = clientRepository.findById(limit.getClientId());
        client.ifPresent(c -> {
            evictClientCache(c.getApiKeyHash());
            logger.info("Updated limits for client {}, evicted cache", limit.getClientId());
        });

        return saved;
    }

    @Transactional
    @CacheEvict(value = "clientConfigs", allEntries = true)
    public void deleteClientLimit(Long id) {
        Optional<ClientLimit> limit = clientLimitRepository.findById(id);
        if (limit.isPresent()) {
            Long clientId = limit.get().getClientId();
            clientLimitRepository.deleteById(id);

            Optional<Client> client = clientRepository.findById(clientId);
            client.ifPresent(c -> {
                evictClientCache(c.getApiKeyHash());
                logger.info("Deleted limits for client {}, evicted cache", clientId);
            });
        }
    }

    private void evictClientCache(String apiKeyHash) {
        try {
            var cache = cacheManager.getCache("clientConfigs");
            if (cache != null) {
                cache.clear();
                logger.debug("Evicted clientConfigs cache");
            }
        } catch (Exception e) {
            logger.error("Failed to evict cache", e);
        }
    }

    public ClientDetailsResponse getClientDetails(Long clientId) {
        if (clientId == null) {
            throw new IllegalArgumentException("Client ID cannot be null");
        }
        Optional<Client> clientOpt = clientRepository.findById(clientId);
        if (clientOpt.isEmpty()) {
            throw new IllegalArgumentException("Client not found: " + clientId);
        }

        Client client = clientOpt.get();
        Optional<ClientLimit> limitOpt = clientLimitRepository.findByClientId(clientId);
        
        if (limitOpt.isEmpty()) {
            return new ClientDetailsResponse(
                ClientDto.fromClient(client),
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

        long windowCount = redisCounter.getWindowCounter(clientIdStr, limit.getWindowSizeSeconds());
        long maxWindowRequests = limit.getMaxRequestsPerWindow();
        double windowUsagePercent = maxWindowRequests > 0 
            ? (double) windowCount / maxWindowRequests * 100 
            : 0.0;
        long windowRemaining = Math.max(0, maxWindowRequests - windowCount);
        Instant windowReset = redisCounter.getWindowResetTime(limit.getWindowSizeSeconds());
        boolean windowSoftThrottled = windowUsagePercent >= (limit.getSoftThrottleThreshold() * 100) && windowUsagePercent < (limit.getHardRejectThreshold() * 100);
        boolean windowBlocked = windowUsagePercent >= (limit.getHardRejectThreshold() * 100);

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

        long monthlyCount = redisCounter.getMonthlyCounter(clientIdStr, yearMonth);
        long monthlyQuota = limit.getMonthlyQuota();
        double monthlyUsagePercent = monthlyQuota > 0 
            ? (double) monthlyCount / monthlyQuota * 100 
            : 0.0;
        long monthlyRemaining = Math.max(0, monthlyQuota - monthlyCount);
        boolean monthlySoftThrottled = monthlyUsagePercent >= (limit.getSoftThrottleThreshold() * 100) && monthlyUsagePercent < (limit.getHardRejectThreshold() * 100);
        boolean monthlyBlocked = monthlyUsagePercent >= (limit.getHardRejectThreshold() * 100);

        ClientDetailsResponse.MonthlyUsage monthlyUsage = new ClientDetailsResponse.MonthlyUsage(
            monthlyCount,
            monthlyQuota,
            monthlyUsagePercent,
            monthlyRemaining,
            monthlySoftThrottled,
            monthlyBlocked
        );

        boolean isBlocked = windowBlocked || monthlyBlocked || !client.getActive();
        boolean isSoftThrottled = (windowSoftThrottled || monthlySoftThrottled) && !isBlocked;
        String statusMessage = buildStatusMessage(isBlocked, isSoftThrottled, windowBlocked, monthlyBlocked, !client.getActive());
        Instant nextReset = windowReset;

        ClientDetailsResponse.ClientStatus status = new ClientDetailsResponse.ClientStatus(
            client.getActive(),
            isBlocked,
            isSoftThrottled,
            statusMessage,
            nextReset
        );

        return new ClientDetailsResponse(
            ClientDto.fromClient(client),
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

    public String hashApiKey(String plainApiKey) {
        return apiKeyHashService.hashApiKey(plainApiKey);
    }

    public String calculateApiKeyIndex(String plainApiKey) {
        return apiKeyHashService.calculateApiKeyIndex(plainApiKey);
    }

    @Transactional
    @CacheEvict(value = {"clientConfigs", "apiKeyValidation"}, allEntries = true)
    public CreateClientResult createClient(String apiKey, String name, Integer priority, Boolean active) {
        String[] hashAndSalt = apiKeyValidationService.hashApiKeyForStorage(apiKey, null);
        String hashedKey = hashAndSalt[0];
        String clientSalt = hashAndSalt[1];
        String apiKeyIndex = apiKeyHashService.calculateApiKeyIndex(apiKey);

        Client client = new Client();
        client.setApiKeyHash(hashedKey);
        client.setApiKeyIndex(apiKeyIndex);
        client.setClientSalt(clientSalt);
        client.setName(name);
        client.setPriority(priority != null ? priority : 0);
        client.setActive(active != null ? active : true);
        client.setAuthMethod("HMAC");
        client.setStatus("ACTIVE");

        Client saved = clientRepository.save(client);
        
        String apiSecret = generateApiSecret(saved.getId());
        
        logger.info("Created client {} with HMAC authentication (ID: {})", name, saved.getId());

        createDefaultClientLimits(saved.getId());
        logger.info("Created default rate limits for client {} (ID: {})", name, saved.getId());

        return new CreateClientResult(saved, apiSecret);
    }

    private void createDefaultClientLimits(Long clientId) {
        ClientLimit defaultLimit = new ClientLimit();
        defaultLimit.setClientId(clientId);
        defaultLimit.setWindowSizeSeconds(defaultWindowSizeSeconds);
        defaultLimit.setMaxRequestsPerWindow(defaultMaxRequestsPerWindow);
        defaultLimit.setMonthlyQuota(defaultMonthlyQuota);
        defaultLimit.setSoftThrottleThreshold(defaultSoftThrottleThreshold);
        defaultLimit.setHardRejectThreshold(defaultHardRejectThreshold);

        clientLimitRepository.save(defaultLimit);
    }

    public Optional<Client> validateApiKey(String plainApiKey) {
        if (plainApiKey == null || plainApiKey.isBlank()) {
            return Optional.empty();
        }

        logger.debug("Validating API key (cache miss)");

        return apiKeyValidationService.validateApiKey(plainApiKey);
    }

    @Transactional
    @CacheEvict(value = {"apiKeyValidation", "clientConfigs"}, allEntries = true)
    public String generateApiSecret(Long clientId) {
        Optional<Client> clientOpt = clientRepository.findById(clientId);
        if (clientOpt.isEmpty()) {
            throw new IllegalArgumentException("Client not found: " + clientId);
        }

        Client client = clientOpt.get();
        
        String apiSecret = generateSecureApiSecret();
        
        String encryptedSecret = cryptoService.encrypt(apiSecret);
        
        client.setApiSecretEncrypted(encryptedSecret);
        client.setAuthMethod("HMAC");
        client.setStatus("ACTIVE");
        
        clientRepository.save(client);
        evictClientCache(client.getApiKeyHash());
        
        logger.info("Generated API secret for client {} (ID: {})", 
                client.getName(), clientId);
        
        return apiSecret;
    }

    @Transactional
    @CacheEvict(value = {"apiKeyValidation", "clientConfigs"}, allEntries = true)
    public String rotateApiSecret(Long clientId) {
        Optional<Client> clientOpt = clientRepository.findById(clientId);
        if (clientOpt.isEmpty()) {
            throw new IllegalArgumentException("Client not found: " + clientId);
        }

        Client client = clientOpt.get();
        
        if (client.getApiSecretEncrypted() == null || client.getApiSecretEncrypted().isBlank()) {
            logger.warn("Client {} (ID: {}) has no API secret - generating new one", 
                    client.getName(), clientId);
        }
        
        String newApiSecret = generateApiSecret(clientId);
        
        logger.info("Rotated API secret for client {} (ID: {})", client.getName(), clientId);
        
        return newApiSecret;
    }


    @Transactional
    @CacheEvict(value = {"apiKeyValidation", "clientConfigs"}, allEntries = true)
    public void updateClientStatus(Long clientId, String status) {
        if (!"ACTIVE".equals(status) && !"SUSPENDED".equals(status) && !"REVOKED".equals(status)) {
            throw new IllegalArgumentException("Invalid status: " + status + ". Must be ACTIVE, SUSPENDED, or REVOKED");
        }

        Optional<Client> clientOpt = clientRepository.findById(clientId);
        if (clientOpt.isEmpty()) {
            throw new IllegalArgumentException("Client not found: " + clientId);
        }

        Client client = clientOpt.get();
        client.setStatus(status);
        
        client.setActive("ACTIVE".equals(status));
        
        clientRepository.save(client);
        evictClientCache(client.getApiKeyHash());
        
        logger.info("Updated status for client {} (ID: {}) to {}", 
                client.getName(), clientId, status);
    }


    private String generateSecureApiSecret() {
        byte[] randomBytes = new byte[apiSecretBytes];
        java.security.SecureRandom secureRandom = new java.security.SecureRandom();
        secureRandom.nextBytes(randomBytes);
        
        return java.util.Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(randomBytes);
    }
}
