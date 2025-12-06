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
import com.irembo.notifications.model.dto.CreateClientResult;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/admin")
@Validated
public class AdminController {

    private static final Logger logger = LoggerFactory.getLogger(AdminController.class);

    @Value("${app.pagination.default-size:20}")
    private int defaultPageSize;

    @Value("${app.pagination.max-size:100}")
    private int maxPageSize;

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

    @GetMapping("/clients/page")
    public ResponseEntity<Page<ClientDto>> getClientsPaginated(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "${app.pagination.default-size:20}") int size,
            @RequestParam(defaultValue = "id") String sort,
            @RequestParam(defaultValue = "ASC") String direction) {

        size = Math.min(size, maxPageSize);

        Sort.Direction sortDirection = direction.equalsIgnoreCase("DESC")
                ? Sort.Direction.DESC
                : Sort.Direction.ASC;

        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sort));
        Page<Client> clients = clientRepository.findAll(pageable);
        Page<ClientDto> clientDtos = clients.map(ClientDto::fromClient);

        return ResponseEntity.ok(clientDtos);
    }

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

    @GetMapping("/clients/{id}")
    public ResponseEntity<?> getClient(@PathVariable @Min(1) Long id) {
        Optional<Client> client = clientRepository.findById(id);
        if (client.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Client not found", "id", id));
        }
        return ResponseEntity.ok(ClientDto.fromClient(client.get()));
    }

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

    @PostMapping("/clients")
    public ResponseEntity<ClientResponse> createClient(@Valid @RequestBody CreateClientRequest request) {
        String apiKey = request.apiKey();
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = apiKeyGeneratorService.generateUniqueApiKey();
        }
        
        CreateClientResult result = adminService.createClient(
            apiKey,
            request.name(),
            request.priority(),
            request.active()
        );
        
        Client saved = result.client();
        String apiSecret = result.apiSecret();
        
        ClientResponse response = ClientResponse.withApiKeyAndSecret(saved, apiKey, apiSecret);
        
        logger.info("Created client {} (ID: {}) with API key and secret. Secret length: {}", 
                saved.getName(), saved.getId(), apiSecret != null ? apiSecret.length() : 0);
        
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/clients/{id}")
    public ResponseEntity<?> updateClient(@PathVariable @Min(1) Long id, @Valid @RequestBody UpdateClientRequest request) {
        Optional<Client> existing = clientRepository.findById(id);
        if (existing.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Client not found", "id", id));
        }

        Client client = existing.get();
        String newApiKey = null;
        Client updated;
        
        if (request.apiKey() != null && !request.apiKey().isBlank()) {
            newApiKey = request.apiKey();
            updated = adminService.updateClientApiKey(id, newApiKey);
        } else {
            if (request.name() != null) {
                client.setName(request.name());
            }
            if (request.priority() != null) {
                client.setPriority(request.priority());
            }
            if (request.active() != null) {
                client.setActive(request.active());
            }
            updated = adminService.updateClient(id, client);
        }
        
        if (newApiKey != null) {
            ClientResponse response = ClientResponse.withApiKey(updated, newApiKey);
            return ResponseEntity.ok(response);
        } else {
            ClientResponse response = ClientResponse.withoutApiKey(updated);
            return ResponseEntity.ok(response);
        }
    }

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

    @DeleteMapping("/clients/{id}")
    public ResponseEntity<?> deleteClient(@PathVariable @Min(1) Long id) {
        if (!clientRepository.existsById(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Client not found", "id", id));
        }
        clientRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "Client deleted", "id", id));
    }

    @GetMapping("/limits/page")
    public ResponseEntity<Page<ClientLimit>> getLimitsPaginated(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        size = Math.min(size, 100);
        Pageable pageable = PageRequest.of(page, size, Sort.by("id"));
        Page<ClientLimit> limits = clientLimitRepository.findAll(pageable);

        return ResponseEntity.ok(limits);
    }

    @GetMapping("/limits/client/{clientId}")
    public ResponseEntity<?> getClientLimit(@PathVariable @Min(1) Long clientId) {
        Optional<ClientLimit> limit = clientLimitRepository.findByClientId(clientId);
        if (limit.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Client limit not found", "clientId", clientId));
        }
        return ResponseEntity.ok(limit.get());
    }

    @PostMapping("/limits")
    public ResponseEntity<ClientLimit> createOrUpdateLimit(@Valid @RequestBody ClientLimit limit) {
        Optional<ClientLimit> existing = clientLimitRepository.findByClientId(limit.getClientId());

        ClientLimit saved = adminService.updateClientLimit(limit);
        return ResponseEntity.status(existing.isPresent() ? HttpStatus.OK : HttpStatus.CREATED).body(saved);
    }

    @DeleteMapping("/limits/{id}")
    public ResponseEntity<?> deleteLimit(@PathVariable @Min(1) Long id) {
        if (!clientLimitRepository.existsById(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Client limit not found", "id", id));
        }
        adminService.deleteClientLimit(id);
        return ResponseEntity.ok(Map.of("message", "Client limit deleted", "id", id));
    }

    @GetMapping("/system-limits")
    public ResponseEntity<List<SystemLimit>> getSystemLimits() {
        return ResponseEntity.ok(systemLimitRepository.findAll());
    }

    @PostMapping("/system-limits")
    public ResponseEntity<SystemLimit> createOrUpdateSystemLimit(@Valid @RequestBody SystemLimitRequest request) {
        Optional<SystemLimit> existing = systemLimitRepository.findByName(request.name());
        
        SystemLimit limit;
        if (existing.isPresent()) {
            limit = existing.get();
            limit.setWindowSizeSeconds(request.windowSizeSeconds());
            limit.setMaxRequestsPerWindow(request.maxRequestsPerWindow());
            limit.setActive(request.active());
        } else {
            limit = new SystemLimit();
            limit.setName(request.name());
            limit.setWindowSizeSeconds(request.windowSizeSeconds());
            limit.setMaxRequestsPerWindow(request.maxRequestsPerWindow());
            limit.setActive(request.active());
        }

        SystemLimit saved = systemLimitRepository.save(limit);
        return ResponseEntity.status(existing.isPresent() ? HttpStatus.OK : HttpStatus.CREATED).body(saved);
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getSystemStatus() {
        long totalClients = clientRepository.count();
        long activeClients = clientRepository.countByActiveTrue();
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

    @PostMapping("/clients/{id}/generate-secret")
    public ResponseEntity<Map<String, Object>> generateApiSecret(@PathVariable @Min(1) Long id) {
        try {
            String apiSecret = adminService.generateApiSecret(id);
            Optional<Client> clientOpt = clientRepository.findById(id);
            
            return ResponseEntity.ok(Map.of(
                    "clientId", id,
                    "apiSecret", apiSecret,
                    "message", "API secret generated successfully. Store it securely - it will not be shown again.",
                    "warning", "⚠️ IMPORTANT: Copy this secret now. It will not be displayed again."
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage(), "clientId", id));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to generate API secret: " + e.getMessage()));
        }
    }

    @PostMapping("/clients/{id}/rotate-secret")
    public ResponseEntity<Map<String, Object>> rotateApiSecret(@PathVariable @Min(1) Long id) {
        try {
            String newApiSecret = adminService.rotateApiSecret(id);
            Optional<Client> clientOpt = clientRepository.findById(id);
            
            return ResponseEntity.ok(Map.of(
                    "clientId", id,
                    "apiSecret", newApiSecret,
                    "message", "API secret rotated successfully. Update your client applications with the new secret.",
                    "warning", "⚠️ IMPORTANT: Copy this secret now. The old secret is no longer valid."
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage(), "clientId", id));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage(), "clientId", id));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to rotate API secret: " + e.getMessage()));
        }
    }

    @PostMapping("/clients/{id}/regenerate-api-key")
    public ResponseEntity<ClientResponse> regenerateApiKey(@PathVariable @Min(1) Long id) {
        try {
            String newApiKey = apiKeyGeneratorService.generateUniqueApiKey();
            
            Client updated = adminService.updateClientApiKey(id, newApiKey);
            
            ClientResponse response = ClientResponse.withApiKey(updated, newApiKey);
            
            logger.info("Regenerated API key for client {} (ID: {})", updated.getName(), id);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ClientResponse.withoutApiKey(null));
        } catch (Exception e) {
            logger.error("Error regenerating API key for client {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ClientResponse.withoutApiKey(null));
        }
    }

    @PutMapping("/clients/{id}/status")
    public ResponseEntity<?> updateClientStatus(
            @PathVariable @Min(1) Long id,
            @Valid @RequestBody Map<String, String> request) {
        
        String status = request.get("status");
        if (status == null || status.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Status is required"));
        }
        
        if (!status.equals("ACTIVE") && !status.equals("SUSPENDED") && !status.equals("REVOKED")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Invalid status. Must be ACTIVE, SUSPENDED, or REVOKED"));
        }
        
        try {
            adminService.updateClientStatus(id, status);
            Optional<Client> clientOpt = clientRepository.findById(id);
            if (clientOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Client not found", "clientId", id));
            }
            
            return ResponseEntity.ok(ClientDto.fromClient(clientOpt.get()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage(), "clientId", id));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to update client status: " + e.getMessage()));
        }
    }
}
