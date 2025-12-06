package com.irembo.notifications.service;

import com.irembo.notifications.infra.db.entity.Client;
import com.irembo.notifications.infra.db.repository.ClientRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;

@Service
public class ApiKeyValidationService {

    private static final Logger logger = LoggerFactory.getLogger(ApiKeyValidationService.class);
    private static final String CACHE_NAME = "apiKeyValidation";

    private final ClientRepository clientRepository;
    private final ApiKeyHashService apiKeyHashService;
    private final CacheManager cacheManager;

    public ApiKeyValidationService(
            ClientRepository clientRepository,
            ApiKeyHashService apiKeyHashService,
            CacheManager cacheManager) {
        this.clientRepository = clientRepository;
        this.apiKeyHashService = apiKeyHashService;
        this.cacheManager = cacheManager;
    }

    public Optional<Client> validateApiKey(String plainApiKey) {
        if (plainApiKey == null || plainApiKey.isBlank()) {
            return Optional.empty();
        }

        Cache cache = cacheManager.getCache(CACHE_NAME);
        if (cache != null) {
            Cache.ValueWrapper wrapper = cache.get(plainApiKey);
            if (wrapper != null) {
                Object cached = wrapper.get();
                if (cached instanceof Client) {
                    logger.debug("API key validation (cache hit)");
                    return Optional.of((Client) cached);
                }
            }
        }

        logger.debug("API key validation (cache miss)");

        String apiKeyIndex = apiKeyHashService.calculateApiKeyIndex(plainApiKey);
        
        Optional<Client> clientOpt = clientRepository.findByApiKeyIndex(apiKeyIndex);
        
        if (clientOpt.isEmpty()) {
            logger.debug("API key validation failed: no client found with matching index");
            return Optional.empty();
        }

        Client client = clientOpt.get();

        if (client.getClientSalt() == null || client.getClientSalt().isBlank()) {
            logger.warn("Client {} has no salt - migration may be incomplete", client.getId());
            return Optional.empty();
        }

        String expectedHash = hashApiKeyWithSalt(plainApiKey, client.getClientSalt());
        
        if (expectedHash.equals(client.getApiKeyHash())) {
            if (!client.getActive()) {
                logger.warn("API key validated for INACTIVE client: {}", client.getId());
            } else {
                logger.debug("API key validated for active client: {}", client.getId());
            }
            
            if (cache != null) {
                cache.put(plainApiKey, client);
            }
            
            return Optional.of(client);
        }

        logger.debug("API key validation failed: hash mismatch for client: {}", client.getId());
        return Optional.empty();
    }

    private String hashApiKeyWithSalt(String plainApiKey, String clientSalt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String input = plainApiKey + clientSalt;
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            logger.error("SHA-256 algorithm not available", e);
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    public String[] hashApiKeyForStorage(String plainApiKey, String existingClientSalt) {
        String clientSalt = existingClientSalt;
        if (clientSalt == null || clientSalt.isBlank()) {
            clientSalt = generateUniqueSalt();
        }
        
        String hashedApiKey = hashApiKeyWithSalt(plainApiKey, clientSalt);
        
        return new String[]{hashedApiKey, clientSalt};
    }

    private String generateUniqueSalt() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String input = System.currentTimeMillis() + "" + Math.random();
            byte[] saltBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            
            StringBuilder hexString = new StringBuilder();
            for (byte b : saltBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            logger.error("SHA-256 algorithm not available", e);
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}

