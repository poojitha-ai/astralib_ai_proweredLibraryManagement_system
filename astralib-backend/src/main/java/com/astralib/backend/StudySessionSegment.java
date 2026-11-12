
package com.astralib.backend;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "study_session_segments")
public class StudySessionSegment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long personalStudySessionId;

    @Column(nullable = false)
    private LocalDateTime segmentStartedAt;

    private LocalDateTime segmentEndedAt;

    @Column(nullable = false)
    private Long durationSeconds = 0L;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();

        if (durationSeconds == null) {
            durationSeconds = 0L;
        }
    }

    public Long getId() {
        return id;
    }

    public Long getPersonalStudySessionId() {
        return personalStudySessionId;
    }

    public void setPersonalStudySessionId(Long personalStudySessionId) {
        this.personalStudySessionId = personalStudySessionId;
    }

    public LocalDateTime getSegmentStartedAt() {
        return segmentStartedAt;
    }

    public void setSegmentStartedAt(LocalDateTime segmentStartedAt) {
        this.segmentStartedAt = segmentStartedAt;
    }

    public LocalDateTime getSegmentEndedAt() {
        return segmentEndedAt;
    }

    public void setSegmentEndedAt(LocalDateTime segmentEndedAt) {
        this.segmentEndedAt = segmentEndedAt;
    }

    public Long getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(Long durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
