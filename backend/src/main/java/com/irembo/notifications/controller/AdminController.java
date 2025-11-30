package com.irembo.notifications.controller;

import com.irembo.notifications.infra.db.entity.Client;
import com.irembo.notifications.infra.db.entity.ClientLimit;
import com.irembo.notifications.infra.db.entity.SystemLimit;
import com.irembo.notifications.infra.db.repository.ClientLimitRepository;
import com.irembo.notifications.infra.db.repository.ClientRepository;
import com.irembo.notifications.infra.db.repository.SystemLimitRepository;
import com.irembo.notifications.model.dto.ClientDetailsResponse;
import com.irembo.notifications.model.dto.ClientDto;
import com.irembo.notifications.model.dto.ClientLimitRequest;
import com.irembo.notifications.model.dto.ClientResponse;
import com.irembo.notifications.model.dto.CreateClientRequest;
import com.irembo.notifications.model.dto.SystemLimitRequest;
import com.irembo.notifications.model.dto.UpdateClientRequest;
import com.irembo.notifications.service.AdminService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Admin Controller for managing clients and rate limits.
 * Protected by HTTP Basic Auth.
 */
@RestController
@RequestMapping("/admin")
@Validated
public class AdminController {

    private final ClientRepository clientRepository;
    private final ClientLimitRepository clientLimitRepository;
    private final SystemLimitRepository systemLimitRepository;
    private final AdminService adminService;
    private final com.irembo.notifications.service.ApiKeyGeneratorService apiKeyGeneratorService;

    public AdminController(
            ClientRepository clientRepository,
            ClientLimitRepository clientLimitRepository,
            SystemLimitRepository systemLimitRepository,
            AdminService adminService,
            com.irembo.notifications.service.ApiKeyGeneratorService apiKeyGeneratorService) {
        this.clientRepository = clientRepository;
        this.clientLimitRepository = clientLimitRepository;
        this.systemLimitRepository = systemLimitRepository;
        this.adminService = adminService;
        this.apiKeyGeneratorService = apiKeyGeneratorService;
    }

    /**
     * Get all clients (non-paginated).
     * @deprecated Use GET /admin/clients/page instead for better performance
     */
    @GetMapping("/clients")
    @Deprecated
    public ResponseEntity<List<ClientDto>> getAllClients() {
        List<Client> clients = clientRepository.findAll();
        List<ClientDto> clientDtos = clients.stream()
                .map(ClientDto::fromClient)
                .toList();
        return ResponseEntity.ok(clientDtos);
    }

    /**
     * Get clients with pagination.
     * @param page Page number (0-indexed, default: 0)
     * @param size Page size (default: 20, max: 100)
     * @param sort Sort field (default: id)
     * @param direction Sort direction (default: ASC)
     */
    @GetMapping("/clients/page")
    public ResponseEntity<Page<ClientDto>> getClientsPaginated(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id") String sort,
            @RequestParam(defaultValue = "ASC") String direction) {

        // Limit max page size to 100
        size = Math.min(size, 100);

        Sort.Direction sortDirection = direction.equalsIgnoreCase("DESC")
                ? Sort.Direction.DESC
                : Sort.Direction.ASC;

        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sort));
        Page<Client> clients = clientRepository.findAll(pageable);
        Page<ClientDto> clientDtos = clients.map(ClientDto::fromClient);

        return ResponseEntity.ok(clientDtos);
    }

    /**
     * Get active clients only with pagination.
     * @param page Page number (0-indexed, default: 0)
     * @param size Page size (default: 20, max: 100)
     */
    @GetMapping("/clients/active")
    public ResponseEntity<Page<ClientDto>> getActiveClients(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        size = Math.min(size, 100);
        Pageable pageable = PageRequest.of(page, size, Sort.by("id"));
        Page<Client> clients = clientRepository.findByActiveTrue(pageable);
        Page<ClientDto> clientDtos = clients.map(ClientDto::fromClient);

        return ResponseEntity.ok(clientDtos);
    }

    /**
     * Get a specific client by ID.
     */
    @GetMapping("/clients/{id}")
    public ResponseEntity<?> getClient(@PathVariable @Min(1) Long id) {
        Optional<Client> client = clientRepository.findById(id);
        if (client.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Client not found", "id", id));
        }
        return ResponseEntity.ok(ClientDto.fromClient(client.get()));
    }

    /**
     * Get complete client details including usage statistics, limits, and status.
     */
    @GetMapping("/clients/{id}/details")
    public ResponseEntity<?> getClientDetails(@PathVariable @Min(1) Long id) {
        try {
            ClientDetailsResponse details = adminService.getClientDetails(id);
            return ResponseEntity.ok(details);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage(), "id", id));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to get client details", "message", e.getMessage()));
        }
    }

    /**
     * Generate a secure API key.
     * Uses cryptographically secure random number generator (SecureRandom).
     *
     * @return A unique, secure API key
     */
    @GetMapping("/generate-api-key")
    public ResponseEntity<Map<String, String>> generateApiKey() {
        try {
            String apiKey = apiKeyGeneratorService.generateUniqueApiKey();
            return ResponseEntity.ok(Map.of(
                    "apiKey", apiKey,
                    "message", "API key generated successfully",
                    "timestamp", java.time.Instant.now().toString()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "error", "Failed to generate API key",
                            "message", e.getMessage(),
                            "timestamp", java.time.Instant.now().toString()
                    ));
        }
    }

    /**
     * Create a new client with hashed API key.
     * If API key is not provided, it will be auto-generated.
     * Returns the API key in plain text ONLY in this response - it's not stored in plain text.
     */
    @PostMapping("/clients")
    public ResponseEntity<ClientResponse> createClient(@Valid @RequestBody CreateClientRequest request) {
        // Generate API key automatically if not provided
        String apiKey = request.apiKey();
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = apiKeyGeneratorService.generateUniqueApiKey();
        }
        
        Client saved = adminService.createClient(
            apiKey,
            request.name(),
            request.priority(),
            request.active()
        );
        // Return API key in response - this is the ONLY time it will be visible
        ClientResponse response = ClientResponse.withApiKey(saved, apiKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Update a client.
     * Note: Cache is evicted for this client when updated.
     * If API key is updated, it will be returned in plain text in the response (only time it's visible).
     */
    @PutMapping("/clients/{id}")
    public ResponseEntity<?> updateClient(@PathVariable @Min(1) Long id, @Valid @RequestBody UpdateClientRequest request) {
        Optional<Client> existing = clientRepository.findById(id);
        if (existing.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Client not found", "id", id));
        }

        Client client = existing.get();
        String newApiKey = null;
        
        // Only update fields that are provided
        if (request.apiKey() != null && !request.apiKey().isBlank()) {
            // Re-hash the API key if it's being updated
            newApiKey = request.apiKey();
            String hashedKey = adminService.hashApiKey(newApiKey);
            client.setApiKeyHash(hashedKey);
        }
        if (request.name() != null) {
            client.setName(request.name());
        }
        if (request.priority() != null) {
            client.setPriority(request.priority());
        }
        if (request.active() != null) {
            client.setActive(request.active());
        }

        Client updated = adminService.updateClient(id, client);
        
        // If API key was updated, return it in the response (only time it's visible)
        if (newApiKey != null) {
            ClientResponse response = ClientResponse.withApiKey(updated, newApiKey);
            return ResponseEntity.ok(response);
        } else {
            // No API key change, return without it
            ClientResponse response = ClientResponse.withoutApiKey(updated);
            return ResponseEntity.ok(response);
        }
    }

    /**
     * Update limits for a specific client.
     * Note: Cache is automatically evicted when limits are updated.
     */
    @PutMapping("/clients/{id}/limits")
    public ResponseEntity<?> updateClientLimits(@PathVariable @Min(1) Long id, @Valid @RequestBody ClientLimitRequest request) {
        if (!clientRepository.existsById(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Client not found", "id", id));
        }

        ClientLimit limit = new ClientLimit();
        limit.setWindowSizeSeconds(request.windowSizeSeconds());
        limit.setMaxRequestsPerWindow(request.maxRequestsPerWindow());
        limit.setMonthlyQuota(request.monthlyQuota());
        if (request.softThrottleThreshold() != null) {
            limit.setSoftThrottleThreshold(request.softThrottleThreshold());
        }
        if (request.hardRejectThreshold() != null) {
            limit.setHardRejectThreshold(request.hardRejectThreshold());
        }

        ClientLimit updated = adminService.createOrUpdateClientLimit(id, limit);
        return ResponseEntity.ok(updated);
    }

    /**
     * Delete a client.
     */
    @DeleteMapping("/clients/{id}")
    public ResponseEntity<?> deleteClient(@PathVariable @Min(1) Long id) {
        if (!clientRepository.existsById(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Client not found", "id", id));
        }
        clientRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "Client deleted", "id", id));
    }

    /**
     * Get all client limits (non-paginated).
     * @deprecated Use GET /admin/limits/page instead for better performance
     */
    @GetMapping("/limits")
    @Deprecated
    public ResponseEntity<List<ClientLimit>> getAllLimits() {
        return ResponseEntity.ok(clientLimitRepository.findAll());
    }

    /**
     * Get client limits with pagination.
     * @param page Page number (0-indexed, default: 0)
     * @param size Page size (default: 20, max: 100)
     */
    @GetMapping("/limits/page")
    public ResponseEntity<Page<ClientLimit>> getLimitsPaginated(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        size = Math.min(size, 100);
        Pageable pageable = PageRequest.of(page, size, Sort.by("id"));
        Page<ClientLimit> limits = clientLimitRepository.findAll(pageable);

        return ResponseEntity.ok(limits);
    }

    /**
     * Get limit for a specific client.
     */
    @GetMapping("/limits/client/{clientId}")
    public ResponseEntity<?> getClientLimit(@PathVariable @Min(1) Long clientId) {
        Optional<ClientLimit> limit = clientLimitRepository.findByClientId(clientId);
        if (limit.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Client limit not found", "clientId", clientId));
        }
        return ResponseEntity.ok(limit.get());
    }

    /**
     * Create or update a client limit.
     * Note: Cache is automatically evicted when limits are updated.
     */
    @PostMapping("/limits")
    public ResponseEntity<ClientLimit> createOrUpdateLimit(@Valid @RequestBody ClientLimit limit) {
        // Check if limit already exists for this client
        Optional<ClientLimit> existing = clientLimitRepository.findByClientId(limit.getClientId());

        ClientLimit saved = adminService.updateClientLimit(limit);
        return ResponseEntity.status(existing.isPresent() ? HttpStatus.OK : HttpStatus.CREATED).body(saved);
    }

    /**
     * Delete a client limit.
     * Note: Cache is automatically evicted when limits are deleted.
     */
    @DeleteMapping("/limits/{id}")
    public ResponseEntity<?> deleteLimit(@PathVariable @Min(1) Long id) {
        if (!clientLimitRepository.existsById(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Client limit not found", "id", id));
        }
        adminService.deleteClientLimit(id);
        return ResponseEntity.ok(Map.of("message", "Client limit deleted", "id", id));
    }

    /**
     * Get all system limits.
     */
    @GetMapping("/system-limits")
    public ResponseEntity<List<SystemLimit>> getSystemLimits() {
        return ResponseEntity.ok(systemLimitRepository.findAll());
    }

    /**
     * Create or update a system limit.
     */
    @PostMapping("/system-limits")
    public ResponseEntity<SystemLimit> createOrUpdateSystemLimit(@Valid @RequestBody SystemLimitRequest request) {
        // Check if limit with this name already exists
        Optional<SystemLimit> existing = systemLimitRepository.findByName(request.name());
        
        SystemLimit limit;
        if (existing.isPresent()) {
            // Update existing
            limit = existing.get();
            limit.setWindowSizeSeconds(request.windowSizeSeconds());
            limit.setMaxRequestsPerWindow(request.maxRequestsPerWindow());
            limit.setActive(request.active());
        } else {
            // Create new
            limit = new SystemLimit();
            limit.setName(request.name());
            limit.setWindowSizeSeconds(request.windowSizeSeconds());
            limit.setMaxRequestsPerWindow(request.maxRequestsPerWindow());
            limit.setActive(request.active());
        }

        SystemLimit saved = systemLimitRepository.save(limit);
        return ResponseEntity.status(existing.isPresent() ? HttpStatus.OK : HttpStatus.CREATED).body(saved);
    }

    /**
     * Get system status and statistics.
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getSystemStatus() {
        long totalClients = clientRepository.count();
        long activeClients = clientRepository.countByActiveTrue(); // ✅ Optimized - single SQL query
        long totalLimits = clientLimitRepository.count();
        long systemLimits = systemLimitRepository.count();

        Map<String, Object> status = Map.of(
                "status", "operational",
                "statistics", Map.of(
                        "totalClients", totalClients,
                        "activeClients", activeClients,
                        "clientLimits", totalLimits,
                        "systemLimits", systemLimits
                )
        );

        return ResponseEntity.ok(status);
    }
}
