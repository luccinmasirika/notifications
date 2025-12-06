package com.irembo.notifications.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Service
public class HMACSignerService {

    private static final Logger logger = LoggerFactory.getLogger(HMACSignerService.class);
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    public String generateSignature(String apiSecret, long timestamp, String httpMethod, String requestPath, String requestBody) {
        if (apiSecret == null || apiSecret.isBlank()) {
            throw new IllegalArgumentException("API secret cannot be null or blank");
        }
        if (httpMethod == null || httpMethod.isBlank()) {
            throw new IllegalArgumentException("HTTP method cannot be null or blank");
        }
        if (requestPath == null) {
            throw new IllegalArgumentException("Request path cannot be null");
        }
        if (requestBody == null) {
            requestBody = "";
        }

        try {
            String payload = buildSignaturePayload(timestamp, httpMethod, requestPath, requestBody);
            
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec secretKeySpec = new SecretKeySpec(apiSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
            mac.init(secretKeySpec);
            
            byte[] signatureBytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            
            return java.util.Base64.getEncoder().encodeToString(signatureBytes);
            
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            logger.error("Error generating HMAC signature", e);
            throw new RuntimeException("HMAC signature generation failed", e);
        }
    }

    public boolean verifySignature(String apiSecret, long timestamp, String httpMethod, String requestPath, String requestBody, String providedSignature) {
        if (apiSecret == null || apiSecret.isBlank() || providedSignature == null || providedSignature.isBlank()) {
            return false;
        }

        try {
            String expectedSignature = generateSignature(apiSecret, timestamp, httpMethod, requestPath, requestBody);
            
            return constantTimeEquals(expectedSignature, providedSignature);
            
        } catch (Exception e) {
            logger.error("Error verifying HMAC signature", e);
            return false;
        }
    }

    private String buildSignaturePayload(long timestamp, String httpMethod, String requestPath, String requestBody) {
        String normalizedPath = requestPath;
        int queryIndex = normalizedPath.indexOf('?');
        if (queryIndex >= 0) {
            normalizedPath = normalizedPath.substring(0, queryIndex);
        }
        
        return timestamp + "\n" + httpMethod.toUpperCase() + "\n" + normalizedPath + "\n" + (requestBody != null ? requestBody : "");
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        if (a.length() != b.length()) {
            return false;
        }
        
        try {
            return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            logger.error("Error in constant-time comparison", e);
            return false;
        }
    }
}

