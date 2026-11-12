
package com.astralib.backend;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "personal_study_sessions",
    indexes = {
        @Index(name = "idx_personal_session_user", columnList = "user_id"),
        @Index(name = "idx_personal_session_status", columnList = "user_id, status"),
        @Index(name = "idx_personal_session_started", columnList = "started_at")
    }
)
public class PersonalStudySession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "plan_task_id")
    private Long planTaskId;

    @Column(nullable = false, length = 150)
    private String subject;

    @Column(nullable = false, length = 200)
    private String title;

    // PERSONAL_TIMER or STUDY_HALL
    @Column(nullable = false, length = 30)
    private String source;

    // Optional Study Hall references
    @Column(name = "study_group_id")
    private Long studyGroupId;

    @Column(name = "study_hall_session_id")
    private Long studyHallSessionId;

    // ACTIVE, PAUSED or COMPLETED
    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "active_segment_started_at")
    private LocalDateTime activeSegmentStartedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    // Completed active seconds, excluding paused time
    @Column(name = "accumulated_seconds", nullable = false)
    private Long accumulatedSeconds = 0L;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public PersonalStudySession() {
    }

    @PrePersist
    public void beforeInsert() {
        LocalDateTime now = LocalDateTime.now();

        if (createdAt == null) {
            createdAt = now;
        }

        if (startedAt == null) {
            startedAt = now;
        }

        if (activeSegmentStartedAt == null && "ACTIVE".equals(status)) {
            activeSegmentStartedAt = now;
        }

        if (status == null || status.isBlank()) {
            status = "ACTIVE";
        }

        if (accumulatedSeconds == null) {
            accumulatedSeconds = 0L;
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getPlanTaskId() {
        return planTaskId;
    }

    public void setPlanTaskId(Long planTaskId) {
        this.planTaskId = planTaskId;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public Long getStudyGroupId() {
        return studyGroupId;
    }

    public void setStudyGroupId(Long studyGroupId) {
        this.studyGroupId = studyGroupId;
    }

    public Long getStudyHallSessionId() {
        return studyHallSessionId;
    }

    public void setStudyHallSessionId(Long studyHallSessionId) {
        this.studyHallSessionId = studyHallSessionId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getActiveSegmentStartedAt() {
        return activeSegmentStartedAt;
    }

    public void setActiveSegmentStartedAt(LocalDateTime activeSegmentStartedAt) {
        this.activeSegmentStartedAt = activeSegmentStartedAt;
    }

    public LocalDateTime getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(LocalDateTime endedAt) {
        this.endedAt = endedAt;
    }

    public Long getAccumulatedSeconds() {
        return accumulatedSeconds;
    }

    public void setAccumulatedSeconds(Long accumulatedSeconds) {
        this.accumulatedSeconds = accumulatedSeconds;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
