package com.irembo.notifications.service;

import com.irembo.notifications.infra.db.repository.ClientRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Service for generating cryptographically secure API keys.
 *
 * Uses SecureRandom for unpredictable key generation.
 */
@Service
public class ApiKeyGeneratorService {

    private static final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.api-key.prefix:LM}")
    private String prefix;

    @Value("${app.api-key.key-bytes:32}")
    private int keyBytes;

    @Value("${app.api-key.max-attempts:10}")
    private int maxAttempts;

    private final ApiKeyHashService apiKeyHashService;
    private final ClientRepository clientRepository;

    public ApiKeyGeneratorService(
            ApiKeyHashService apiKeyHashService,
            ClientRepository clientRepository) {
        this.apiKeyHashService = apiKeyHashService;
        this.clientRepository = clientRepository;
    }

    /**
     * Generate a cryptographically secure API key.
     *
     * Format: LM-{base64url-encoded-random-bytes}
     * Example: LM-xK7v9mP2qL4wN8jR5tY1uI3oE6aS0dF9gH2bV4cX7zM
     *
     * @return A secure, URL-safe API key
     */
    public String generateSecureApiKey() {
        byte[] randomBytes = new byte[keyBytes];
        secureRandom.nextBytes(randomBytes);

        String encoded = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(randomBytes);

        return prefix + "-" + encoded;
    }

    /**
     * Generate a unique API key that doesn't exist in the database.
     *
     * This method checks against existing hashed API keys to ensure uniqueness.
     * In the extremely rare case of a collision, it generates a new key.
     *
     * @return A unique, secure API key
     */
    public String generateUniqueApiKey() {
        String apiKey;
        String hashedKey;
        int attempts = 0;

        do {
            apiKey = generateSecureApiKey();
            hashedKey = apiKeyHashService.hashApiKey(apiKey);
            attempts++;

            if (attempts >= maxAttempts) {
                throw new RuntimeException("Failed to generate unique API key after " + maxAttempts + " attempts");
            }
        } while (clientRepository.existsByApiKeyHash(hashedKey));

        return apiKey;
    }

    /**
     * Generate a human-readable API key with segments (for better UX).
     *
     * Format: LM-XXX-XXXXXX-XXXXXX-XXXXXX
     * Example: LM-A7K-P9M2Q1-L4W8N6-R5T3Y0
     *
     * @return A segmented, secure API key
     */
    public String generateSegmentedApiKey() {
        byte[] randomBytes = new byte[16]; // 128 bits
        secureRandom.nextBytes(randomBytes);

        // Convert to alphanumeric characters (Base32-like)
        StringBuilder apiKey = new StringBuilder(prefix);
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

        for (int i = 0; i < randomBytes.length; i++) {
            int value = randomBytes[i] & 0xFF;
            apiKey.append(chars.charAt(value % chars.length()));

            // Add dashes for readability: LM-XXX-XXXXXX-XXXXXX-XXXXXX
            if (i == 2 || i == 8 || i == 14) {
                apiKey.append("-");
            }
        }

        return apiKey.toString();
    }
}
