package com.irembo.notifications.infra.db.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "client_limit")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClientLimit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_id", nullable = false, unique = true)
    private Long clientId;

    @Column(name = "window_size_seconds", nullable = false)
    private Integer windowSizeSeconds;

    @Column(name = "max_requests_per_window", nullable = false)
    private Integer maxRequestsPerWindow;

    @Column(name = "monthly_quota", nullable = false)
    private Integer monthlyQuota;

    @Column(name = "soft_throttle_threshold", nullable = false)
    private Double softThrottleThreshold = 0.80;

    @Column(name = "hard_reject_threshold", nullable = false)
    private Double hardRejectThreshold = 1.00;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
