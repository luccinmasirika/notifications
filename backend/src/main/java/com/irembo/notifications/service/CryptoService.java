package com.irembo.notifications.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Service for encrypting/decrypting API secrets using AES-256-GCM.
 * 
 * Security:
 * - AES-256-GCM: Authenticated encryption (prevents tampering)
 * - Random IV for each encryption (prevents pattern analysis)
 * - Master key stored in environment variable
 * - GCM tag for authentication (128 bits)
 * 
 * Format: base64(IV (12 bytes) + Encrypted Secret + GCM Tag (16 bytes))
 * 
 * Performance:
 * - Encryption: ~1-2ms
 * - Decryption: ~1-2ms
 */
@Service
public class CryptoService {

    private static final Logger logger = LoggerFactory.getLogger(CryptoService.class);
    
    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128; // bits
    private static final int GCM_IV_LENGTH = 12; // bytes (96 bits recommended for GCM)
    private static final int AES_KEY_SIZE = 256; // bits

    private final SecretKey masterKey;
    private final SecureRandom secureRandom;

    public CryptoService(@Value("${app.crypto.master-key:}") String masterKeyBase64) {
        if (masterKeyBase64 == null || masterKeyBase64.isBlank()) {
            logger.warn("Master key not configured. Generating a new one (NOT SECURE FOR PRODUCTION!)");
            this.masterKey = generateNewMasterKey();
            logger.error("⚠️  CRITICAL: Using auto-generated master key. Set app.crypto.master-key in production!");
        } else {
            try {
                byte[] keyBytes = Base64.getDecoder().decode(masterKeyBase64);
                if (keyBytes.length != 32) { // AES-256 requires 32 bytes
                    throw new IllegalArgumentException("Master key must be 32 bytes (256 bits) when base64 decoded");
                }
                this.masterKey = new SecretKeySpec(keyBytes, ALGORITHM);
                logger.info("CryptoService initialized with provided master key");
            } catch (IllegalArgumentException e) {
                logger.error("Invalid master key format. Must be base64-encoded 32-byte key.", e);
                throw new RuntimeException("Invalid master key configuration", e);
            }
        }
        
        this.secureRandom = new SecureRandom();
    }

    /**
     * Encrypt an API secret using AES-256-GCM.
     * 
     * @param plainSecret The plain text API secret
     * @return Base64-encoded string: IV (12 bytes) + Encrypted Secret + GCM Tag (16 bytes)
     */
    public String encrypt(String plainSecret) {
        if (plainSecret == null || plainSecret.isBlank()) {
            throw new IllegalArgumentException("Secret cannot be null or blank");
        }

        try {
            // Generate random IV (12 bytes for GCM)
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            // Initialize cipher for encryption
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, masterKey, parameterSpec);

            // Encrypt
            byte[] encryptedBytes = cipher.doFinal(plainSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            // Combine IV + encrypted data (IV is prepended)
            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + encryptedBytes.length);
            byteBuffer.put(iv);
            byteBuffer.put(encryptedBytes);

            // Return base64-encoded result
            return Base64.getEncoder().encodeToString(byteBuffer.array());

        } catch (Exception e) {
            logger.error("Error encrypting secret", e);
            throw new RuntimeException("Encryption failed", e);
        }
    }

    /**
     * Decrypt an encrypted API secret.
     * 
     * @param encryptedSecret Base64-encoded encrypted secret (IV + encrypted data + GCM tag)
     * @return Plain text API secret
     */
    public String decrypt(String encryptedSecret) {
        if (encryptedSecret == null || encryptedSecret.isBlank()) {
            throw new IllegalArgumentException("Encrypted secret cannot be null or blank");
        }

        try {
            // Decode base64
            byte[] cipherText = Base64.getDecoder().decode(encryptedSecret);

            if (cipherText.length < GCM_IV_LENGTH + 16) { // IV (12) + minimum encrypted data (16)
                throw new IllegalArgumentException("Invalid encrypted secret format");
            }

            // Extract IV (first 12 bytes)
            ByteBuffer byteBuffer = ByteBuffer.wrap(cipherText);
            byte[] iv = new byte[GCM_IV_LENGTH];
            byteBuffer.get(iv);

            // Extract encrypted data (remaining bytes)
            byte[] encryptedData = new byte[byteBuffer.remaining()];
            byteBuffer.get(encryptedData);

            // Initialize cipher for decryption
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, masterKey, parameterSpec);

            // Decrypt
            byte[] decryptedBytes = cipher.doFinal(encryptedData);

            return new String(decryptedBytes, java.nio.charset.StandardCharsets.UTF_8);

        } catch (Exception e) {
            logger.error("Error decrypting secret", e);
            throw new RuntimeException("Decryption failed", e);
        }
    }

    /**
     * Generate a new master key (for development/testing only).
     * In production, the master key should be provided via environment variable.
     */
    private SecretKey generateNewMasterKey() {
        try {
            KeyGenerator keyGenerator = KeyGenerator.getInstance(ALGORITHM);
            keyGenerator.init(AES_KEY_SIZE);
            return keyGenerator.generateKey();
        } catch (Exception e) {
            logger.error("Error generating master key", e);
            throw new RuntimeException("Failed to generate master key", e);
        }
    }

    /**
     * Generate a base64-encoded master key for configuration.
     * This method can be used to generate a master key that can be stored in environment variables.
     * 
     * @return Base64-encoded 32-byte key
     */
    public static String generateMasterKeyBase64() {
        try {
            KeyGenerator keyGenerator = KeyGenerator.getInstance(ALGORITHM);
            keyGenerator.init(AES_KEY_SIZE);
            SecretKey key = keyGenerator.generateKey();
            return Base64.getEncoder().encodeToString(key.getEncoded());
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate master key", e);
        }
    }
}

