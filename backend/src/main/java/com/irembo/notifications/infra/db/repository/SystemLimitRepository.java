package com.irembo.notifications.infra.db.repository;

import com.irembo.notifications.infra.db.entity.SystemLimit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SystemLimitRepository extends JpaRepository<SystemLimit, Long> {

    Optional<SystemLimit> findByName(String name);

    Optional<SystemLimit> findByNameAndActiveTrue(String name);
}
