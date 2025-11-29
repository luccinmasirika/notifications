package com.irembo.notifications.controller;

import com.irembo.notifications.infra.db.entity.Client;
import com.irembo.notifications.infra.db.entity.ClientLimit;
import com.irembo.notifications.infra.db.entity.SystemLimit;
import com.irembo.notifications.infra.db.repository.ClientLimitRepository;
import com.irembo.notifications.infra.db.repository.ClientRepository;
import com.irembo.notifications.infra.db.repository.SystemLimitRepository;
import com.irembo.notifications.model.dto.ClientDetailsResponse;
import com.irembo.notifications.model.dto.ClientLimitRequest;
import com.irembo.notifications.model.dto.CreateClientRequest;
import com.irembo.notifications.model.dto.SystemLimitRequest;
import com.irembo.notifications.model.dto.UpdateClientRequest;
import com.irembo.notifications.service.AdminService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
public class AdminController {

    private final ClientRepository clientRepository;
    private final ClientLimitRepository clientLimitRepository;
    private final SystemLimitRepository systemLimitRepository;
    private final AdminService adminService;

    public AdminController(
            ClientRepository clientRepository,
            ClientLimitRepository clientLimitRepository,
            SystemLimitRepository systemLimitRepository,
            AdminService adminService) {
        this.clientRepository = clientRepository;
        this.clientLimitRepository = clientLimitRepository;
        this.systemLimitRepository = systemLimitRepository;
        this.adminService = adminService;
    }

    /**
     * Get all clients.
     */
    @GetMapping("/clients")
    public ResponseEntity<List<Client>> getAllClients() {
        return ResponseEntity.ok(clientRepository.findAll());
    }

    /**
     * Get a specific client by ID.
     */
    @GetMapping("/clients/{id}")
    public ResponseEntity<?> getClient(@PathVariable Long id) {
        Optional<Client> client = clientRepository.findById(id);
        if (client.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Client not found", "id", id));
        }
        return ResponseEntity.ok(client.get());
    }

    /**
     * Get complete client details including usage statistics, limits, and status.
     */
    @GetMapping("/clients/{id}/details")
    public ResponseEntity<?> getClientDetails(@PathVariable Long id) {
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
     * Create a new client.
     */
    @PostMapping("/clients")
    public ResponseEntity<Client> createClient(@Valid @RequestBody CreateClientRequest request) {
        Client client = new Client();
        client.setApiKey(request.apiKey());
        client.setName(request.name());
        client.setPriority(request.priority());
        client.setActive(request.active());

        Client saved = clientRepository.save(client);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    /**
     * Update a client.
     * Note: Cache is evicted for this client when updated.
     */
    @PutMapping("/clients/{id}")
    public ResponseEntity<?> updateClient(@PathVariable Long id, @Valid @RequestBody UpdateClientRequest request) {
        Optional<Client> existing = clientRepository.findById(id);
        if (existing.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Client not found", "id", id));
        }

        Client client = existing.get();
        // Only update fields that are provided
        if (request.apiKey() != null) {
            client.setApiKey(request.apiKey());
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
        return ResponseEntity.ok(updated);
    }

    /**
     * Update limits for a specific client.
     * Note: Cache is automatically evicted when limits are updated.
     */
    @PutMapping("/clients/{id}/limits")
    public ResponseEntity<?> updateClientLimits(@PathVariable Long id, @Valid @RequestBody ClientLimitRequest request) {
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
    public ResponseEntity<?> deleteClient(@PathVariable Long id) {
        if (!clientRepository.existsById(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Client not found", "id", id));
        }
        clientRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "Client deleted", "id", id));
    }

    /**
     * Get all client limits.
     */
    @GetMapping("/limits")
    public ResponseEntity<List<ClientLimit>> getAllLimits() {
        return ResponseEntity.ok(clientLimitRepository.findAll());
    }

    /**
     * Get limit for a specific client.
     */
    @GetMapping("/limits/client/{clientId}")
    public ResponseEntity<?> getClientLimit(@PathVariable Long clientId) {
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
    public ResponseEntity<ClientLimit> createOrUpdateLimit(@RequestBody ClientLimit limit) {
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
    public ResponseEntity<?> deleteLimit(@PathVariable Long id) {
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
        long activeClients = clientRepository.findAll().stream()
                .filter(Client::getActive)
                .count();
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
