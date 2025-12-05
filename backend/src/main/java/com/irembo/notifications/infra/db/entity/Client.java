package com.irembo.notifications.infra.db.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "client")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Client {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "api_key_hash", unique = true, nullable = false, length = 255)
    private String apiKeyHash; // BCrypt hash of the API key (60 chars)

    @Column(name = "api_key_index", unique = true, nullable = true, length = 64)
    private String apiKeyIndex; // SHA-256 hash for fast O(1) lookup (64 hex chars)

    @Column(name = "client_salt", unique = true, nullable = true, length = 64)
    private String clientSalt; // Unique salt per client for fast SHA-256 validation (64 hex chars)

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "priority")
    private Integer priority = 0;

    @Column(name = "active")
    private Boolean active = true;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
