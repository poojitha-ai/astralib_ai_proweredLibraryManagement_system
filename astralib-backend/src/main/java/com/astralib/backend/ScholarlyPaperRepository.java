package com.astralib.backend;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ScholarlyPaperRepository extends JpaRepository<ScholarlyPaper, Long> {
    Optional<ScholarlyPaper> findByResourceId(String resourceId);
    Optional<ScholarlyPaper> findByOpenalexId(String openalexId);
}
