package com.astralib.backend;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StudySessionRepository
        extends JpaRepository<StudySession, Long> {

    List<StudySession>
    findByStudyGroupIdOrderByCreatedAtDesc(
            Long studyGroupId
    );

    List<StudySession>
    findByStudyGroupIdAndStatusOrderByScheduledAtAsc(
            Long studyGroupId,
            String status
    );

    Optional<StudySession>
    findFirstByStudyGroupIdAndStatusOrderByStartedAtDesc(
            Long studyGroupId,
            String status
    );

    boolean existsByStudyGroupIdAndStatus(
            Long studyGroupId,
            String status
    );
}