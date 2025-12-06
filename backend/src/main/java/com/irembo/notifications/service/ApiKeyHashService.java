package com.irembo.notifications.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Service
public class ApiKeyHashService {

    private static final Logger logger = LoggerFactory.getLogger(ApiKeyHashService.class);

    private static final int BCRYPT_STRENGTH = 12;

    private final BCryptPasswordEncoder encoder;

    public ApiKeyHashService() {
        this.encoder = new BCryptPasswordEncoder(BCRYPT_STRENGTH);
        logger.info("ApiKeyHashService initialized with BCrypt strength: {}", BCRYPT_STRENGTH);
    }

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

    public boolean isValidBCryptHash(String hash) {
        if (hash == null) {
            return false;
        }

        return hash.length() == 60 && hash.matches("^\\$2[ayb]\\$\\d{2}\\$.{53}$");
    }

    public String calculateApiKeyIndex(String plainApiKey) {
        if (plainApiKey == null || plainApiKey.isBlank()) {
            throw new IllegalArgumentException("API key cannot be null or blank");
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(plainApiKey.getBytes(StandardCharsets.UTF_8));
            
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
