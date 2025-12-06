package com.irembo.notifications.infra.db.repository;

import com.irembo.notifications.infra.db.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByClientId(Long clientId);

    List<Notification> findByStatus(String status);

    List<Notification> findByClientIdAndStatus(Long clientId, String status);

    Optional<Notification> findById(Long id);
}
