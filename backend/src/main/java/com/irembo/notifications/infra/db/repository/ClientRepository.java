package com.irembo.notifications.infra.db.repository;

import com.irembo.notifications.infra.db.entity.Client;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ClientRepository extends JpaRepository<Client, Long> {

    Optional<Client> findByApiKeyHash(String apiKeyHash);

    boolean existsByApiKeyHash(String apiKeyHash);

    /**
     * Count active clients efficiently using a database query.
     * Replaces the inefficient findAll().stream().filter() pattern.
     */
    @Query("SELECT COUNT(c) FROM Client c WHERE c.active = true")
    long countActiveClients();

    /**
     * Alternative using Spring Data query derivation.
     * Both methods work, but @Query is more explicit.
     */
    long countByActiveTrue();

    /**
     * Find all clients with pagination support.
     * Inherited from JpaRepository, but explicitly declared for clarity.
     */
    Page<Client> findAll(Pageable pageable);

    /**
     * Find only active clients with pagination support.
     */
    Page<Client> findByActiveTrue(Pageable pageable);
}
