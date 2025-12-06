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

/**
 * Service for generating and verifying HMAC-SHA256 signatures.
 * 
 * Signature Format:
 * HMAC-SHA256(api_secret, timestamp + "\n" + HTTP_METHOD + "\n" + REQUEST_PATH + "\n" + JSON_BODY)
 * 
 * Security:
 * - Constant-time comparison to prevent timing attacks
 * - HMAC-SHA256 is cryptographically secure
 * - API secret never transmitted (only signature)
 * 
 * Performance:
 * - Signature generation: ~0.1ms
 * - Signature verification: ~0.1ms
 */
@Service
public class HMACSignerService {

    private static final Logger logger = LoggerFactory.getLogger(HMACSignerService.class);
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    /**
     * Generate HMAC-SHA256 signature for a request.
     * 
     * @param apiSecret The API secret (plain text, will be decrypted from storage)
     * @param timestamp Request timestamp (Unix milliseconds)
     * @param httpMethod HTTP method (GET, POST, PUT, DELETE, etc.)
     * @param requestPath Request path (e.g., "/api/notifications")
     * @param requestBody JSON body (empty string for GET requests)
     * @return Base64-encoded HMAC-SHA256 signature
     */
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
            // Build signature payload
            String payload = buildSignaturePayload(timestamp, httpMethod, requestPath, requestBody);
            
            // Create HMAC instance
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec secretKeySpec = new SecretKeySpec(apiSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
            mac.init(secretKeySpec);
            
            // Generate signature
            byte[] signatureBytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            
            // Return base64-encoded signature
            return java.util.Base64.getEncoder().encodeToString(signatureBytes);
            
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            logger.error("Error generating HMAC signature", e);
            throw new RuntimeException("HMAC signature generation failed", e);
        }
    }

    /**
     * Verify HMAC-SHA256 signature.
     * Uses constant-time comparison to prevent timing attacks.
     * 
     * @param apiSecret The API secret
     * @param timestamp Request timestamp
     * @param httpMethod HTTP method
     * @param requestPath Request path
     * @param requestBody JSON body
     * @param providedSignature The signature provided in X-SIGNATURE header
     * @return true if signature is valid, false otherwise
     */
    public boolean verifySignature(String apiSecret, long timestamp, String httpMethod, String requestPath, String requestBody, String providedSignature) {
        if (apiSecret == null || apiSecret.isBlank() || providedSignature == null || providedSignature.isBlank()) {
            return false;
        }

        try {
            // Generate expected signature
            String expectedSignature = generateSignature(apiSecret, timestamp, httpMethod, requestPath, requestBody);
            
            // Constant-time comparison
            return constantTimeEquals(expectedSignature, providedSignature);
            
        } catch (Exception e) {
            logger.error("Error verifying HMAC signature", e);
            return false;
        }
    }

    /**
     * Build the signature payload string.
     * Format: timestamp + "\n" + HTTP_METHOD + "\n" + REQUEST_PATH + "\n" + JSON_BODY
     * 
     * @param timestamp Request timestamp
     * @param httpMethod HTTP method
     * @param requestPath Request path
     * @param requestBody JSON body
     * @return Signature payload string
     */
    private String buildSignaturePayload(long timestamp, String httpMethod, String requestPath, String requestBody) {
        // Normalize request path (remove query string for signature)
        String normalizedPath = requestPath;
        int queryIndex = normalizedPath.indexOf('?');
        if (queryIndex >= 0) {
            normalizedPath = normalizedPath.substring(0, queryIndex);
        }
        
        // Build payload: timestamp + "\n" + method + "\n" + path + "\n" + body
        return timestamp + "\n" + httpMethod.toUpperCase() + "\n" + normalizedPath + "\n" + (requestBody != null ? requestBody : "");
    }

    /**
     * Constant-time string comparison to prevent timing attacks.
     * 
     * @param a First string
     * @param b Second string
     * @return true if strings are equal, false otherwise
     */
    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        if (a.length() != b.length()) {
            return false;
        }
        
        // Use MessageDigest.isEqual for constant-time comparison
        try {
            return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            logger.error("Error in constant-time comparison", e);
            return false;
        }
    }

    /**
     * Calculate SHA-256 hash of request body for consistent body representation.
     * This can be used if you want to hash the body instead of using raw JSON.
     * Currently not used, but available for future enhancements.
     * 
     * @param body Request body
     * @return SHA-256 hash (hex string)
     */
    @SuppressWarnings("unused")
    private String hashBody(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(body.getBytes(StandardCharsets.UTF_8));
            
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

