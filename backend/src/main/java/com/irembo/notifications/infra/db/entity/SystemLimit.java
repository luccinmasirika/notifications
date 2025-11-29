package com.irembo.notifications.infra.db.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "system_limit")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SystemLimit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", unique = true, nullable = false)
    @Size(min = 1, max = 255, message = "Name must be between 1 and 255 characters")
    @Pattern(
            regexp = "^[a-zA-Z0-9_]+$",
            message = "Name must contain only letters, numbers, and underscores (format: limit_123)"
    )
    private String name;

    @Column(name = "window_size_seconds", nullable = false)
    private Integer windowSizeSeconds;

    @Column(name = "max_requests_per_window", nullable = false)
    private Integer maxRequestsPerWindow;

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
