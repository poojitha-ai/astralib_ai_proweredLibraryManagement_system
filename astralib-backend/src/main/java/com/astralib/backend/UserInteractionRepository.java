package com.astralib.backend;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserInteractionRepository extends JpaRepository<UserInteraction, Long> {
    List<UserInteraction> findByUser_IdOrderByTimestampDesc(Long userId);
    List<UserInteraction> findByUser_IdAndResourceId(Long userId, String resourceId);
}
