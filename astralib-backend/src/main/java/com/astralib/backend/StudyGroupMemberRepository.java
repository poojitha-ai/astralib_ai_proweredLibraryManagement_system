package com.astralib.backend;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StudyGroupMemberRepository
        extends JpaRepository<
                StudyGroupMember,
                Long
        > {

    boolean existsByStudyGroupIdAndUserId(
            Long studyGroupId,
            Long userId
    );

    Optional<StudyGroupMember>
    findByStudyGroupIdAndUserId(
            Long studyGroupId,
            Long userId
    );

    long countByStudyGroupId(
            Long studyGroupId
    );

    List<StudyGroupMember>
    findByStudyGroupIdOrderByJoinedAtAsc(
            Long studyGroupId
    );

    List<StudyGroupMember>
    findByUserIdOrderByJoinedAtDesc(
            Long userId
    );
}