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
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

/**
 * API Key Authentication Filter.
 * Validates X-API-KEY header for API endpoints.
 * Runs BEFORE RateLimiterFilter to ensure only valid clients consume rate limits.
 */
@Component
@Order(1) // Run before RateLimiterFilter (which has default order)
public class APIKeyAuthFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(APIKeyAuthFilter.class);

    private static final String API_KEY_HEADER = "X-API-KEY";

    private final AdminService adminService;
    private final ObjectMapper objectMapper;

    public APIKeyAuthFilter(AdminService adminService, ObjectMapper objectMapper) {
        this.adminService = adminService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();

        // Skip authentication for public endpoints and admin endpoints
        if (shouldSkipApiKeyAuth(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Extract API key from header
        String apiKey = request.getHeader(API_KEY_HEADER);

        // Check if API key is present
        if (apiKey == null || apiKey.isBlank()) {
            logger.warn("Missing X-API-KEY header for path: {}", path);
            sendUnauthorizedResponse(response, "Missing X-API-KEY header");
            return;
        }

        // Validate API key (supports both hashed and plain text for backward compatibility)
        Optional<Client> clientOpt = adminService.validateApiKey(apiKey);

        if (clientOpt.isEmpty()) {
            logger.warn("Invalid API key attempted: {}", maskApiKey(apiKey));
            sendUnauthorizedResponse(response, "Invalid API key");
            return;
        }

        Client client = clientOpt.get();

        // Check if client is active
        if (!client.getActive()) {
            logger.warn("Inactive client attempted access: {} (ID: {})", client.getName(), client.getId());
            sendUnauthorizedResponse(response, "Client account is inactive");
            return;
        }

        // API key is valid - continue with the request
        logger.debug("API key validated for client: {} (ID: {})", client.getName(), client.getId());

        // Store client info in request attribute for downstream filters/controllers
        request.setAttribute("authenticatedClient", client);
        request.setAttribute("clientId", client.getId());
        request.setAttribute("clientName", client.getName());

        filterChain.doFilter(request, response);
    }

    /**
     * Determine if API key authentication should be skipped for this path.
     * Skip for: health checks, actuator endpoints, admin endpoints, swagger/openapi, error pages.
     */
    private boolean shouldSkipApiKeyAuth(String path) {
        return path.equals("/health") ||
               path.equals("/actuator/health") ||
               path.startsWith("/actuator/") ||
               path.startsWith("/admin/") ||
               path.startsWith("/swagger-ui") ||
               path.startsWith("/v3/api-docs") ||
               path.startsWith("/api-docs") ||
               path.startsWith("/error");
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

    /**
     * Mask API key for logging (show only last 4 characters).
     */
    private String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() <= 4) {
            return "****";
        }
        return "****" + apiKey.substring(apiKey.length() - 4);
    }
}
