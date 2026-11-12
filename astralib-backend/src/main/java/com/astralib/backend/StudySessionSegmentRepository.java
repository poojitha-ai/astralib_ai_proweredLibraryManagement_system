
package com.astralib.backend;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface StudySessionSegmentRepository
        extends JpaRepository<StudySessionSegment, Long> {

    List<StudySessionSegment>
    findByPersonalStudySessionIdOrderBySegmentStartedAtAsc(
            Long personalStudySessionId
    );

    Optional<StudySessionSegment>
    findFirstByPersonalStudySessionIdAndSegmentEndedAtIsNullOrderBySegmentStartedAtDesc(
            Long personalStudySessionId
    );

    @Query("""
            SELECT segment
            FROM StudySessionSegment segment
            JOIN PersonalStudySession session
              ON session.id = segment.personalStudySessionId
            WHERE session.userId = :userId
              AND segment.segmentStartedAt < :rangeEnd
              AND (
                    segment.segmentEndedAt IS NULL
                    OR segment.segmentEndedAt > :rangeStart
              )
            ORDER BY segment.segmentStartedAt ASC
            """)
    List<StudySessionSegment> findUserSegmentsWithinRange(
            @Param("userId") Long userId,
            @Param("rangeStart") LocalDateTime rangeStart,
            @Param("rangeEnd") LocalDateTime rangeEnd
    );
}
