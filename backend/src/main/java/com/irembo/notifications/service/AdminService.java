package com.irembo.notifications.service;

import com.irembo.notifications.infra.db.entity.Client;
import com.irembo.notifications.infra.db.entity.ClientLimit;
import com.irembo.notifications.infra.db.repository.ClientLimitRepository;
import com.irembo.notifications.infra.db.repository.ClientRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public AdminService(
            ClientRepository clientRepository,
            ClientLimitRepository clientLimitRepository,
            CacheManager cacheManager) {
        this.clientRepository = clientRepository;
        this.clientLimitRepository = clientLimitRepository;
        this.cacheManager = cacheManager;
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
     * Mask API key for logging (show only last 4 characters).
     */
    private String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() <= 4) {
            return "****";
        }
        return apiKey.substring(apiKey.length() - 4);
    }
}
