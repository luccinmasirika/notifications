package com.irembo.notifications.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.irembo.notifications.infra.db.entity.Client;
import com.irembo.notifications.service.AdminService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.irembo.notifications.config.FilterPathMatcher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

@Component
@Order(1)
public class APIKeyAuthFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(APIKeyAuthFilter.class);

    private static final String API_KEY_HEADER = "X-API-KEY";

    private final AdminService adminService;
    private final ObjectMapper objectMapper;
    private final FilterPathMatcher filterPathMatcher;

    @Value("${app.filter.masking.api-key-visible-chars:4}")
    private int apiKeyVisibleChars;

    public APIKeyAuthFilter(
            AdminService adminService, 
            ObjectMapper objectMapper,
            FilterPathMatcher filterPathMatcher) {
        this.adminService = adminService;
        this.objectMapper = objectMapper;
        this.filterPathMatcher = filterPathMatcher;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();

        if (filterPathMatcher.shouldSkip(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        String apiKey = request.getHeader(API_KEY_HEADER);

        if (apiKey == null || apiKey.isBlank()) {
            logger.warn("Missing X-API-KEY header for path: {}", path);
            sendUnauthorizedResponse(response, "Missing X-API-KEY header");
            return;
        }

        Optional<Client> clientOpt = adminService.validateApiKey(apiKey);

        if (clientOpt.isEmpty()) {
            logger.warn("Invalid API key attempted: {}", maskApiKeyForLogging(apiKey));
            sendUnauthorizedResponse(response, "Invalid API key");
            return;
        }

        Client client = clientOpt.get();

        String status = client.getStatus() != null ? client.getStatus() : "ACTIVE";
        if (!"ACTIVE".equals(status)) {
            logger.warn("Client {} (ID: {}) has status {} - access denied", 
                    client.getName(), client.getId(), status);
            sendUnauthorizedResponse(response, "Client account is " + status.toLowerCase());
            return;
        }

        if (!client.getActive()) {
            logger.warn("Inactive client attempted access: {} (ID: {})", client.getName(), client.getId());
            sendUnauthorizedResponse(response, "Client account is inactive");
            return;
        }

        String signature = request.getHeader("X-SIGNATURE");
        if (signature == null || signature.isBlank()) {
            logger.warn("Client {} (ID: {}) - HMAC authentication required. Missing X-SIGNATURE header.", 
                    client.getName(), client.getId());
            sendUnauthorizedResponse(response, "HMAC authentication required. Missing X-SIGNATURE header.");
            return;
        }
        
        String timestamp = request.getHeader("X-TIMESTAMP");
        if (timestamp == null || timestamp.isBlank()) {
            logger.warn("Client {} (ID: {}) - HMAC authentication required. Missing X-TIMESTAMP header.", 
                    client.getName(), client.getId());
            sendUnauthorizedResponse(response, "HMAC authentication required. Missing X-TIMESTAMP header.");
            return;
        }
        
        String authMethod = client.getAuthMethod() != null ? client.getAuthMethod() : "HMAC";
        if (!"HMAC".equals(authMethod)) {
            logger.warn("Client {} (ID: {}) uses {} auth but HMAC is required", 
                    client.getName(), client.getId(), authMethod);
            sendUnauthorizedResponse(response, "HMAC authentication is required for all clients");
            return;
        }
        
        logger.debug("Client {} (ID: {}) - HMAC headers present, signature validation will be performed", 
                client.getName(), client.getId());

        request.setAttribute("authenticatedClient", client);
        request.setAttribute("clientId", client.getId());
        request.setAttribute("clientName", client.getName());
        request.setAttribute("authMethod", authMethod);

        filterChain.doFilter(request, response);
    }


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

    private String maskApiKeyForLogging(String apiKey) {
        if (apiKey == null || apiKey.length() <= apiKeyVisibleChars) {
            return "****";
        }
        return "****" + apiKey.substring(apiKey.length() - apiKeyVisibleChars);
    }
}
