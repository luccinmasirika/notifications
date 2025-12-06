package com.irembo.notifications.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.irembo.notifications.infra.db.entity.Client;
import com.irembo.notifications.config.FilterPathMatcher;
import com.irembo.notifications.service.CryptoService;
import com.irembo.notifications.service.HMACSignerService;
import com.irembo.notifications.service.TimestampValidator;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Signature Validation Filter for HMAC-SHA256 authentication.
 * 
 * This filter runs AFTER APIKeyAuthFilter (Order 2) to validate HMAC signatures.
 * HMAC authentication is REQUIRED for all clients (legacy support removed).
 * 
 * Headers required:
 * - X-API-KEY: Public API key (client identifier)
 * - X-TIMESTAMP: Unix timestamp in milliseconds
 * - X-SIGNATURE: HMAC-SHA256 signature
 * 
 * Security:
 * - Constant-time signature comparison
 * - Timestamp validation (prevents replay attacks)
 * - API secret never transmitted
 */
@Component
@Order(2) // Run after APIKeyAuthFilter (Order 1) to validate HMAC signature (required for all clients)
public class SignatureValidationFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(SignatureValidationFilter.class);

    private static final String API_KEY_HEADER = "X-API-KEY";
    private static final String TIMESTAMP_HEADER = "X-TIMESTAMP";
    private static final String SIGNATURE_HEADER = "X-SIGNATURE";

    private final CryptoService cryptoService;
    private final HMACSignerService hmacSignerService;
    private final TimestampValidator timestampValidator;
    private final ObjectMapper objectMapper;
    private final FilterPathMatcher filterPathMatcher;

    public SignatureValidationFilter(
            CryptoService cryptoService,
            HMACSignerService hmacSignerService,
            TimestampValidator timestampValidator,
            ObjectMapper objectMapper,
            FilterPathMatcher filterPathMatcher) {
        this.cryptoService = cryptoService;
        this.hmacSignerService = hmacSignerService;
        this.timestampValidator = timestampValidator;
        this.objectMapper = objectMapper;
        this.filterPathMatcher = filterPathMatcher;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();

        // Skip signature validation for public endpoints
        if (filterPathMatcher.shouldSkip(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        // HMAC authentication is required for all requests
        String apiKey = request.getHeader(API_KEY_HEADER);
        String timestampStr = request.getHeader(TIMESTAMP_HEADER);
        String signature = request.getHeader(SIGNATURE_HEADER);

        // HMAC authentication required - validate all headers
        if (apiKey == null || apiKey.isBlank()) {
            logger.warn("HMAC auth requested but X-API-KEY header missing");
            sendUnauthorizedResponse(response, "Missing X-API-KEY header");
            return;
        }

        if (timestampStr == null || timestampStr.isBlank()) {
            logger.warn("HMAC auth requested but X-TIMESTAMP header missing");
            sendUnauthorizedResponse(response, "Missing X-TIMESTAMP header");
            return;
        }

        // Parse timestamp
        long timestamp;
        try {
            timestamp = Long.parseLong(timestampStr);
        } catch (NumberFormatException e) {
            logger.warn("Invalid X-TIMESTAMP format: {}", timestampStr);
            sendUnauthorizedResponse(response, "Invalid X-TIMESTAMP format");
            return;
        }

        // Validate timestamp (prevent replay attacks)
        if (!timestampValidator.isValid(timestamp)) {
            logger.warn("Timestamp validation failed: timestamp={}", timestamp);
            sendUnauthorizedResponse(response, "Request timestamp is outside acceptable window");
            return;
        }

        // Get request body (needed for signature verification)
        String requestBody = getRequestBody(request);

        // Get client from request attribute (set by APIKeyAuthFilter)
        Client client = (Client) request.getAttribute("authenticatedClient");
        
        if (client == null) {
            // Client not yet authenticated - APIKeyAuthFilter should have run first
            logger.warn("Client not found in request attributes - APIKeyAuthFilter should run first");
            sendUnauthorizedResponse(response, "Client authentication required before signature validation");
            return;
        }

        // All clients must use HMAC auth
        String authMethod = client.getAuthMethod() != null ? client.getAuthMethod() : "HMAC";
        if (!"HMAC".equals(authMethod)) {
            logger.error("Client {} (ID: {}) has invalid auth_method: {}. HMAC is required.", 
                    client.getName(), client.getId(), authMethod);
            sendUnauthorizedResponse(response, "Client configuration error: HMAC authentication required");
            return;
        }

        // Check client status
        if (!"ACTIVE".equals(client.getStatus())) {
            logger.warn("Client {} status is {} - access denied", client.getId(), client.getStatus());
            sendUnauthorizedResponse(response, "Client account is " + client.getStatus().toLowerCase());
            return;
        }

        // Decrypt API secret
        String apiSecret;
        try {
            if (client.getApiSecretEncrypted() == null || client.getApiSecretEncrypted().isBlank()) {
                logger.error("Client {} has HMAC auth but no encrypted secret", client.getId());
                sendUnauthorizedResponse(response, "Client configuration error");
                return;
            }
            apiSecret = cryptoService.decrypt(client.getApiSecretEncrypted());
        } catch (Exception e) {
            logger.error("Error decrypting API secret for client {}", client.getId(), e);
            sendUnauthorizedResponse(response, "Authentication error");
            return;
        }

        // Verify signature
        String httpMethod = request.getMethod();
        String requestPath = request.getRequestURI();
        
        boolean signatureValid = hmacSignerService.verifySignature(
                apiSecret,
                timestamp,
                httpMethod,
                requestPath,
                requestBody,
                signature
        );

        if (!signatureValid) {
            logger.warn("Invalid HMAC signature for client {} (ID: {})", client.getName(), client.getId());
            sendUnauthorizedResponse(response, "Invalid signature");
            return;
        }

        // Signature is valid - continue with request
        logger.debug("HMAC signature validated for client: {} (ID: {})", client.getName(), client.getId());
        
        // Store validation result in request attribute
        request.setAttribute("hmacSignatureValidated", true);

        filterChain.doFilter(request, response);
    }

    /**
     * Get request body as string.
     * Uses CachedBodyHttpServletRequest to read body without consuming the stream.
     * The ContentCachingFilter should wrap the request before this filter runs.
     */
    private String getRequestBody(HttpServletRequest request) throws IOException {
        // Check if request is wrapped with our custom wrapper
        if (request instanceof ContentCachingFilter.CachedBodyHttpServletRequest) {
            ContentCachingFilter.CachedBodyHttpServletRequest wrapper = 
                (ContentCachingFilter.CachedBodyHttpServletRequest) request;
            return wrapper.getCachedBodyAsString();
        }

        // Fallback: if not wrapped, try to read directly (should not happen if ContentCachingFilter runs first)
        logger.warn("Request not wrapped with CachedBodyHttpServletRequest - body may be consumed. " +
                   "Ensure ContentCachingFilter runs before SignatureValidationFilter.");
        try {
            return StreamUtils.copyToString(request.getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.debug("Could not read request body: {}", e.getMessage());
            return "";
        }
    }

    /**
     * Send 401 Unauthorized response with JSON body.
     */
    private void sendUnauthorizedResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        Map<String, Object> errorBody = Map.of(
                "error", "Unauthorized",
                "status", 401,
                "message", message,
                "timestamp", System.currentTimeMillis()
        );

        String jsonResponse = objectMapper.writeValueAsString(errorBody);
        response.getWriter().write(jsonResponse);
        response.getWriter().flush();
    }
}

