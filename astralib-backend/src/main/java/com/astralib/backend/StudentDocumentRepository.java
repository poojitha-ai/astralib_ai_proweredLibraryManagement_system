package com.astralib.backend;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StudentDocumentRepository extends JpaRepository<StudentDocument, Long> {

    List<StudentDocument> findByUser_IdOrderByCreatedAtDesc(Long userId);

    Optional<StudentDocument> findByIdAndUser_Id(Long id, Long userId);

    long countByUser_Id(Long userId);

    void deleteByIdAndUser_Id(Long id, Long userId);
}
