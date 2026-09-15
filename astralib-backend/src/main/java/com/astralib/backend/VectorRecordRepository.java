package com.astralib.backend;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VectorRecordRepository extends JpaRepository<VectorRecord, Long> {
    Optional<VectorRecord> findByChunkId(String chunkId);
    boolean existsByChunkId(String chunkId);
    List<VectorRecord> findByDocumentId(String documentId);
    List<VectorRecord> findByDocumentIdContainingIgnoreCase(String documentId);
    List<VectorRecord> findByDocumentType(String documentType);
    long countByDocumentType(String documentType);
}
