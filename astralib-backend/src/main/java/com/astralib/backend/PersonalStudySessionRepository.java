
package com.astralib.backend;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

@Repository
public interface PersonalStudySessionRepository
        extends JpaRepository<PersonalStudySession, Long> {

    List<PersonalStudySession> findByUserIdOrderByStartedAtDesc(
        Long userId
    );

    Optional<PersonalStudySession> findByIdAndUserId(
        Long id,
        Long userId
    );

    Optional<PersonalStudySession>
        findFirstByUserIdAndStatusInOrderByStartedAtDesc(
            Long userId,
            List<String> statuses
        );

    List<PersonalStudySession>
        findByUserIdAndStatusOrderByStartedAtDesc(
            Long userId,
            String status
        );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT s FROM PersonalStudySession s
        WHERE s.id = :sessionId AND s.userId = :userId
        """)
    Optional<PersonalStudySession> findByIdAndUserIdForUpdate(
        @Param("sessionId") Long sessionId,
        @Param("userId") Long userId
    );
}
