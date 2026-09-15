package com.astralib.backend;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SavedResearchRepository
        extends JpaRepository<SavedResearch, Long> {

    List<SavedResearch> findByUser_IdOrderByCreatedAtDesc(
            Long userId
    );

    Optional<SavedResearch> findByIdAndUser_Id(
            Long id,
            Long userId
    );
}
