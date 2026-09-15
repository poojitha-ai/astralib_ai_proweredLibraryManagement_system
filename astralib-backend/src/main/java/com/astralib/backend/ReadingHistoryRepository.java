package com.astralib.backend;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReadingHistoryRepository
        extends JpaRepository<ReadingHistory, Long> {

    List<ReadingHistory>
    findByUser_IdOrderByAccessTimeDesc(Long userId);

    Optional<ReadingHistory>
    findByUser_IdAndBook_IdAndResource_Id(
            Long userId,
            Long bookId,
            Long resourceId
    );

    Optional<ReadingHistory>
    findFirstByUser_IdAndBook_IdOrderByAccessTimeDesc(
            Long userId,
            Long bookId
    );
}