package com.irembo.notifications.infra.db.repository;

import com.irembo.notifications.infra.db.entity.ClientLimit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ClientLimitRepository extends JpaRepository<ClientLimit, Long> {

    Optional<ClientLimit> findByClientId(Long clientId);

    void deleteByClientId(Long clientId);
}
