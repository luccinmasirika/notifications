package com.irembo.notifications.controller;

import com.irembo.notifications.infra.db.entity.Client;
import com.irembo.notifications.infra.db.entity.ClientLimit;
import com.irembo.notifications.infra.db.entity.SystemLimit;
import com.irembo.notifications.infra.db.repository.ClientLimitRepository;
import com.irembo.notifications.infra.db.repository.ClientRepository;
import com.irembo.notifications.infra.db.repository.SystemLimitRepository;
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

    public AdminController(
            ClientRepository clientRepository,
            ClientLimitRepository clientLimitRepository,
            SystemLimitRepository systemLimitRepository) {
        this.clientRepository = clientRepository;
        this.clientLimitRepository = clientLimitRepository;
        this.systemLimitRepository = systemLimitRepository;
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
     * Create a new client.
     */
    @PostMapping("/clients")
    public ResponseEntity<Client> createClient(@RequestBody Client client) {
        client.setId(null); // Ensure new ID is generated
        Client saved = clientRepository.save(client);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    /**
     * Update a client.
     */
    @PutMapping("/clients/{id}")
    public ResponseEntity<?> updateClient(@PathVariable Long id, @RequestBody Client client) {
        if (!clientRepository.existsById(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Client not found", "id", id));
        }
        client.setId(id);
        Client updated = clientRepository.save(client);
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
     */
    @PostMapping("/limits")
    public ResponseEntity<ClientLimit> createOrUpdateLimit(@RequestBody ClientLimit limit) {
        // Check if limit already exists for this client
        Optional<ClientLimit> existing = clientLimitRepository.findByClientId(limit.getClientId());
        if (existing.isPresent()) {
            limit.setId(existing.get().getId());
        }

        ClientLimit saved = clientLimitRepository.save(limit);
        return ResponseEntity.status(existing.isPresent() ? HttpStatus.OK : HttpStatus.CREATED).body(saved);
    }

    /**
     * Delete a client limit.
     */
    @DeleteMapping("/limits/{id}")
    public ResponseEntity<?> deleteLimit(@PathVariable Long id) {
        if (!clientLimitRepository.existsById(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Client limit not found", "id", id));
        }
        clientLimitRepository.deleteById(id);
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
    public ResponseEntity<SystemLimit> createOrUpdateSystemLimit(@RequestBody SystemLimit limit) {
        // Check if limit with this name already exists
        Optional<SystemLimit> existing = systemLimitRepository.findByName(limit.getName());
        if (existing.isPresent()) {
            limit.setId(existing.get().getId());
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
