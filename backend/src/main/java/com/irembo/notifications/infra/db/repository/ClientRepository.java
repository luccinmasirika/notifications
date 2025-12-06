package com.irembo.notifications.infra.db.repository;

import com.irembo.notifications.infra.db.entity.Client;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClientRepository extends JpaRepository<Client, Long> {

    Optional<Client> findByApiKeyHash(String apiKeyHash);

    boolean existsByApiKeyHash(String apiKeyHash);

    Optional<Client> findByApiKeyIndex(String apiKeyIndex);

    @Query("SELECT COUNT(c) FROM Client c WHERE c.active = true")
    long countActiveClients();

    long countByActiveTrue();

    Page<Client> findAll(Pageable pageable);

    Page<Client> findByActiveTrue(Pageable pageable);

    List<Client> findByActiveTrue();
}
