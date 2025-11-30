package com.irembo.notifications.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Service for hashing and validating API keys using BCrypt.
 *
 * BCrypt is used because:
 * - It's slow by design (prevents brute force attacks)
 * - It includes a salt automatically
 * - It's adaptive (can increase rounds as hardware improves)
 *
 * Strength level 12 is chosen as a balance between:
 * - Security: Strong enough against current hardware
 * - Performance: Fast enough for API key validation (~200ms)
 */
@Service
public class ApiKeyHashService {

    private static final Logger logger = LoggerFactory.getLogger(ApiKeyHashService.class);

    // BCrypt strength level: 12 = 2^12 rounds (4096 iterations)
    // Higher = more secure but slower
    // 12 is recommended for 2024+ (takes ~200ms to hash)
    private static final int BCRYPT_STRENGTH = 12;

    private final BCryptPasswordEncoder encoder;

    public ApiKeyHashService() {
        this.encoder = new BCryptPasswordEncoder(BCRYPT_STRENGTH);
        logger.info("ApiKeyHashService initialized with BCrypt strength: {}", BCRYPT_STRENGTH);
    }

    /**
     * Hash a plain text API key using BCrypt.
     *
     * @param plainApiKey The plain text API key
     * @return BCrypt hash (60 characters, starts with $2a$)
     */
    public String hashApiKey(String plainApiKey) {
        if (plainApiKey == null || plainApiKey.isBlank()) {
            throw new IllegalArgumentException("API key cannot be null or blank");
        }

        long startTime = System.currentTimeMillis();
        String hash = encoder.encode(plainApiKey);
        long duration = System.currentTimeMillis() - startTime;

        logger.debug("API key hashed in {}ms", duration);
        return hash;
    }

    /**
     * Verify that a plain text API key matches a BCrypt hash.
     *
     * This is a constant-time operation to prevent timing attacks.
     *
     * @param plainApiKey The plain text API key to verify
     * @param hashedApiKey The BCrypt hash to compare against
     * @return true if the API key matches the hash, false otherwise
     */
    public boolean matches(String plainApiKey, String hashedApiKey) {
        if (plainApiKey == null || hashedApiKey == null) {
            return false;
        }

        try {
            long startTime = System.currentTimeMillis();
            boolean result = encoder.matches(plainApiKey, hashedApiKey);
            long duration = System.currentTimeMillis() - startTime;

            logger.debug("API key verification completed in {}ms (result: {})", duration, result);
            return result;
        } catch (Exception e) {
            logger.error("Error verifying API key hash", e);
            return false;
        }
    }

    /**
     * Check if a string is a valid BCrypt hash.
     *
     * BCrypt hashes have the format: $2a$rounds$salt+hash
     * They are always 60 characters long.
     *
     * @param hash The string to check
     * @return true if it's a valid BCrypt hash format
     */
    public boolean isValidBCryptHash(String hash) {
        if (hash == null) {
            return false;
        }

        // BCrypt hashes are always 60 characters and start with $2a$ or $2b$ or $2y$
        return hash.length() == 60 && hash.matches("^\\$2[ayb]\\$\\d{2}\\$.{53}$");
    }
}
