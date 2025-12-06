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

@Service
public class CryptoService {

    private static final Logger logger = LoggerFactory.getLogger(CryptoService.class);
    
    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int GCM_IV_LENGTH = 12;
    private static final int AES_KEY_SIZE = 256;

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
                if (keyBytes.length != 32) {
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

    public String encrypt(String plainSecret) {
        if (plainSecret == null || plainSecret.isBlank()) {
            throw new IllegalArgumentException("Secret cannot be null or blank");
        }

        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, masterKey, parameterSpec);

            byte[] encryptedBytes = cipher.doFinal(plainSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + encryptedBytes.length);
            byteBuffer.put(iv);
            byteBuffer.put(encryptedBytes);

            return Base64.getEncoder().encodeToString(byteBuffer.array());

        } catch (Exception e) {
            logger.error("Error encrypting secret", e);
            throw new RuntimeException("Encryption failed", e);
        }
    }

    public String decrypt(String encryptedSecret) {
        if (encryptedSecret == null || encryptedSecret.isBlank()) {
            throw new IllegalArgumentException("Encrypted secret cannot be null or blank");
        }

        try {
            byte[] cipherText = Base64.getDecoder().decode(encryptedSecret);

            if (cipherText.length < GCM_IV_LENGTH + 16) {
                throw new IllegalArgumentException("Invalid encrypted secret format");
            }

            ByteBuffer byteBuffer = ByteBuffer.wrap(cipherText);
            byte[] iv = new byte[GCM_IV_LENGTH];
            byteBuffer.get(iv);

            byte[] encryptedData = new byte[byteBuffer.remaining()];
            byteBuffer.get(encryptedData);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, masterKey, parameterSpec);

            byte[] decryptedBytes = cipher.doFinal(encryptedData);

            return new String(decryptedBytes, java.nio.charset.StandardCharsets.UTF_8);

        } catch (Exception e) {
            logger.error("Error decrypting secret", e);
            throw new RuntimeException("Decryption failed", e);
        }
    }

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

