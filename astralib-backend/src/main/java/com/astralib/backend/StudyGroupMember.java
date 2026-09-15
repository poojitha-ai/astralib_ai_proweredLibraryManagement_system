package com.astralib.backend;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "study_group_members",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_study_group_user",
                        columnNames = {
                                "study_group_id",
                                "user_id"
                        }
                )
        }
)
public class StudyGroupMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(
            name = "study_group_id",
            nullable = false
    )
    private Long studyGroupId;

    @Column(
            name = "user_id",
            nullable = false
    )
    private Long userId;

    @Column(
            name = "joined_at",
            nullable = false
    )
    private LocalDateTime joinedAt;

    public StudyGroupMember() {
    }

    @PrePersist
    public void prePersist() {
        if (joinedAt == null) {
            joinedAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getStudyGroupId() {
        return studyGroupId;
    }

    public void setStudyGroupId(
            Long studyGroupId
    ) {
        this.studyGroupId = studyGroupId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(
            Long userId
    ) {
        this.userId = userId;
    }

    public LocalDateTime getJoinedAt() {
        return joinedAt;
    }

    public void setJoinedAt(
            LocalDateTime joinedAt
    ) {
        this.joinedAt = joinedAt;
    }
}