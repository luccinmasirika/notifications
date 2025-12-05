package com.irembo.notifications.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

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
 *
 * Performance optimization:
 * - Uses SHA-256 index for O(1) database lookup
 * - Only performs BCrypt verification on the matching client (1 verification max)
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

    /**
     * Calculate SHA-256 hash of an API key for fast database lookup.
     *
     * This is a deterministic hash (same input = same output) used as an index
     * to enable O(1) database lookup instead of O(n) BCrypt verification loop.
     *
     * Security note: SHA-256 alone is NOT secure for password/API key storage.
     * It's only used as a fast lookup index. The actual security comes from BCrypt.
     *
     * @param plainApiKey Plain text API key
     * @return SHA-256 hash (64 hex characters)
     */
    public String calculateApiKeyIndex(String plainApiKey) {
        if (plainApiKey == null || plainApiKey.isBlank()) {
            throw new IllegalArgumentException("API key cannot be null or blank");
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(plainApiKey.getBytes(StandardCharsets.UTF_8));
            
            // Convert bytes to hex string
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
}
