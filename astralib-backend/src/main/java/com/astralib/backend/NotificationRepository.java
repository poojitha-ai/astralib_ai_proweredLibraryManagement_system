package com.astralib.backend;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationRepository
        extends JpaRepository<Notification, Long> {

    List<Notification>
    findByUserIdOrderByCreatedAtDesc(
            Long userId
    );

    long countByUserIdAndReadFlagFalse(
            Long userId
    );

    Optional<Notification>
    findByIdAndUserId(
            Long id,
            Long userId
    );

    List<Notification>
    findByUserIdAndReadFlagFalse(
            Long userId
    );
}