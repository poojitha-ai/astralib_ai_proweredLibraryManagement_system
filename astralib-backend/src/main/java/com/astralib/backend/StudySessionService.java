package com.astralib.backend;

import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class StudySessionService {

    private final StudySessionRepository
            studySessionRepository;

    private final StudyGroupRepository
            studyGroupRepository;

    private final StudyGroupMemberRepository
            studyGroupMemberRepository;

    private final NotificationService
            notificationService;

    public StudySessionService(
            StudySessionRepository studySessionRepository,
            StudyGroupRepository studyGroupRepository,
            StudyGroupMemberRepository studyGroupMemberRepository,
            NotificationService notificationService
    ) {

        this.studySessionRepository =
                studySessionRepository;

        this.studyGroupRepository =
                studyGroupRepository;

        this.studyGroupMemberRepository =
                studyGroupMemberRepository;

        this.notificationService =
                notificationService;
    }

    // =========================================================
    // SCHEDULE SESSION
    // =========================================================

    @Transactional
    public Map<String, Object>
    scheduleSession(
            Long groupId,
            Long ownerUserId,
            Map<String, Object> request
    ) {

        StudyGroup group =
                findGroup(groupId);

        validateOwner(
                group,
                ownerUserId
        );

        String title =
                getString(
                        request,
                        "title"
                );

        if (title.isBlank()) {

            throw new IllegalArgumentException(
                    "Session title is required"
            );
        }

        String agenda =
                getString(
                        request,
                        "agenda"
                );

        String scheduledAtText =
                getString(
                        request,
                        "scheduledAt"
                );

        if (scheduledAtText.isBlank()) {

            throw new IllegalArgumentException(
                    "Session date and time are required"
            );
        }

        LocalDateTime scheduledAt =
                parseDateTime(
                        scheduledAtText
                );

        /*
         * Prevent accidentally scheduling a session
         * significantly in the past.
         */
        if (
                scheduledAt.isBefore(
                        LocalDateTime
                                .now()
                                .minusMinutes(1)
                )
        ) {

            throw new IllegalArgumentException(
                    "Session time must be in the future"
            );
        }

        StudySession session =
                new StudySession();

        session.setStudyGroupId(
                groupId
        );

        session.setTitle(
                title
        );

        session.setAgenda(
                agenda
        );

        session.setScheduledAt(
                scheduledAt
        );

        session.setStatus(
                "SCHEDULED"
        );

        session.setCreatedBy(
                ownerUserId
        );

        StudySession savedSession =
                studySessionRepository
                        .save(session);

        /*
         * Inform every group member except
         * the owner who scheduled it.
         */
        notificationService
                .notifyGroupMembers(
                        groupId,
                        ownerUserId,
                        "STUDY_SESSION_SCHEDULED",
                        "Study session scheduled",
                        group.getName()
                                + " scheduled \""
                                + title
                                + "\" for "
                                + formatDateTime(
                                        scheduledAt
                                )
                                + ".",
                        savedSession.getId()
                );

        return toResponse(
                savedSession,
                group,
                ownerUserId
        );
    }

    // =========================================================
    // START A NEW SESSION IMMEDIATELY
    // =========================================================

    @Transactional
    public Map<String, Object>
    startNow(
            Long groupId,
            Long ownerUserId,
            Map<String, Object> request
    ) {

        StudyGroup group =
                findGroup(groupId);

        validateOwner(
                group,
                ownerUserId
        );

        validateNoLiveSession(
                groupId
        );

        String title =
                getString(
                        request,
                        "title"
                );

        if (title.isBlank()) {

            title =
                    "Live Study Session";
        }

        String agenda =
                getString(
                        request,
                        "agenda"
                );

        LocalDateTime now =
                LocalDateTime.now();

        StudySession session =
                new StudySession();

        session.setStudyGroupId(
                groupId
        );

        session.setTitle(
                title
        );

        session.setAgenda(
                agenda
        );

        session.setScheduledAt(
                now
        );

        session.setStartedAt(
                now
        );

        session.setStatus(
                "LIVE"
        );

        session.setCreatedBy(
                ownerUserId
        );

        StudySession savedSession =
                studySessionRepository
                        .save(session);

        sendStartedNotification(
                group,
                savedSession,
                ownerUserId
        );

        return toResponse(
                savedSession,
                group,
                ownerUserId
        );
    }

    // =========================================================
    // START A SCHEDULED SESSION
    // =========================================================

    @Transactional
    public Map<String, Object>
    startSession(
            Long sessionId,
            Long ownerUserId
    ) {

        StudySession session =
                findSession(
                        sessionId
                );

        StudyGroup group =
                findGroup(
                        session.getStudyGroupId()
                );

        validateOwner(
                group,
                ownerUserId
        );

        if (
                !"SCHEDULED".equalsIgnoreCase(
                        session.getStatus()
                )
        ) {

            throw new IllegalArgumentException(
                    "Only a scheduled session can be started"
            );
        }

        validateNoLiveSession(
                group.getId()
        );

        session.setStatus(
                "LIVE"
        );

        session.setStartedAt(
                LocalDateTime.now()
        );

        StudySession savedSession =
                studySessionRepository
                        .save(session);

        sendStartedNotification(
                group,
                savedSession,
                ownerUserId
        );

        return toResponse(
                savedSession,
                group,
                ownerUserId
        );
    }

    // =========================================================
    // END LIVE SESSION
    // =========================================================

    @Transactional
    public Map<String, Object>
    endSession(
            Long sessionId,
            Long ownerUserId
    ) {

        StudySession session =
                findSession(
                        sessionId
                );

        StudyGroup group =
                findGroup(
                        session.getStudyGroupId()
                );

        validateOwner(
                group,
                ownerUserId
        );

        if (
                !"LIVE".equalsIgnoreCase(
                        session.getStatus()
                )
        ) {

            throw new IllegalArgumentException(
                    "Only a live session can be ended"
            );
        }

        session.setStatus(
                "ENDED"
        );

        session.setEndedAt(
                LocalDateTime.now()
        );

        StudySession savedSession =
                studySessionRepository
                        .save(session);

        return toResponse(
                savedSession,
                group,
                ownerUserId
        );
    }

    // =========================================================
    // GET ALL SESSIONS OF ONE GROUP
    // =========================================================

    public List<Map<String, Object>>
    getGroupSessions(
            Long groupId,
            Long currentUserId
    ) {

        StudyGroup group =
                findGroup(groupId);

        validateMember(
                groupId,
                currentUserId
        );

        List<StudySession> sessions =
                studySessionRepository
                        .findByStudyGroupIdOrderByCreatedAtDesc(
                                groupId
                        );

        List<Map<String, Object>> result =
                new ArrayList<>();

        for (StudySession session : sessions) {

            result.add(
                    toResponse(
                            session,
                            group,
                            currentUserId
                    )
            );
        }

        return result;
    }

    // =========================================================
    // CURRENT ROOM SESSION INFORMATION
    // =========================================================

    public Map<String, Object>
    getCurrentSessionStatus(
            Long groupId,
            Long currentUserId
    ) {

        StudyGroup group =
                findGroup(groupId);

        validateMember(
                groupId,
                currentUserId
        );

        StudySession liveSession =
                studySessionRepository
                        .findFirstByStudyGroupIdAndStatusOrderByStartedAtDesc(
                                groupId,
                                "LIVE"
                        )
                        .orElse(null);

        List<StudySession> scheduledSessions =
                studySessionRepository
                        .findByStudyGroupIdAndStatusOrderByScheduledAtAsc(
                                groupId,
                                "SCHEDULED"
                        );

        StudySession nextSession =
                null;

        LocalDateTime now =
                LocalDateTime.now();

        for (StudySession session : scheduledSessions) {

            if (
                    session.getScheduledAt() != null
                            &&
                    !session.getScheduledAt()
                            .isBefore(now)
            ) {

                nextSession =
                        session;

                break;
            }
        }

        /*
         * If a scheduled session has just passed but
         * the owner has not started it yet, still show
         * the earliest scheduled session.
         */
        if (
                nextSession == null
                        &&
                !scheduledSessions.isEmpty()
        ) {

            nextSession =
                    scheduledSessions.get(0);
        }

        Map<String, Object> response =
                new LinkedHashMap<>();

        response.put(
                "groupId",
                groupId
        );

        response.put(
                "groupName",
                group.getName()
        );

        response.put(
                "live",
                liveSession != null
        );

        response.put(
                "liveSession",
                liveSession == null
                        ? null
                        : toResponse(
                                liveSession,
                                group,
                                currentUserId
                        )
        );

        response.put(
                "nextSession",
                nextSession == null
                        ? null
                        : toResponse(
                                nextSession,
                                group,
                                currentUserId
                        )
        );

        return response;
    }

    // =========================================================
    // SEND "DISCUSSION STARTED" NOTIFICATION
    // =========================================================

    private void sendStartedNotification(
            StudyGroup group,
            StudySession session,
            Long ownerUserId
    ) {

        notificationService
                .notifyGroupMembers(
                        group.getId(),
                        ownerUserId,
                        "STUDY_SESSION_STARTED",
                        group.getName()
                                + " discussion started",
                        "\""
                                + session.getTitle()
                                + "\" is now live. "
                                + "Join the study room.",
                        session.getId()
                );
    }

    // =========================================================
    // MAKE SURE ONLY ONE LIVE SESSION EXISTS
    // =========================================================

    private void validateNoLiveSession(
            Long groupId
    ) {

        boolean liveSessionExists =
                studySessionRepository
                        .existsByStudyGroupIdAndStatus(
                                groupId,
                                "LIVE"
                        );

        if (liveSessionExists) {

            throw new IllegalArgumentException(
                    "A study session is already live for this group"
            );
        }
    }

    // =========================================================
    // OWNER VALIDATION
    // =========================================================

    private void validateOwner(
            StudyGroup group,
            Long currentUserId
    ) {

        if (
                currentUserId == null
                        ||
                !group.getCreatorId()
                        .equals(currentUserId)
        ) {

            throw new IllegalArgumentException(
                    "Only the study group owner can manage study sessions"
            );
        }
    }

    // =========================================================
    // MEMBER VALIDATION
    // =========================================================

    private void validateMember(
            Long groupId,
            Long currentUserId
    ) {

        if (currentUserId == null) {

            throw new IllegalArgumentException(
                    "Authenticated user not found"
            );
        }

        boolean member =
                studyGroupMemberRepository
                        .existsByStudyGroupIdAndUserId(
                                groupId,
                                currentUserId
                        );

        if (!member) {

            throw new IllegalArgumentException(
                    "You must be a member of this study group"
            );
        }
    }

    // =========================================================
    // FIND GROUP
    // =========================================================

    private StudyGroup findGroup(
            Long groupId
    ) {

        return studyGroupRepository
                .findById(groupId)
                .orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        "Study group not found"
                                )
                );
    }

    // =========================================================
    // FIND SESSION
    // =========================================================

    private StudySession findSession(
            Long sessionId
    ) {

        return studySessionRepository
                .findById(sessionId)
                .orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        "Study session not found"
                                )
                );
    }

    // =========================================================
    // PARSE DATE / TIME
    // =========================================================

    private LocalDateTime parseDateTime(
            String value
    ) {

        try {

            return LocalDateTime.parse(
                    value
            );

        } catch (DateTimeParseException e) {

            throw new IllegalArgumentException(
                    "Invalid session date and time"
            );
        }
    }

    // =========================================================
    // DISPLAY DATE / TIME
    // =========================================================

    private String formatDateTime(
            LocalDateTime value
    ) {

        DateTimeFormatter formatter =
                DateTimeFormatter.ofPattern(
                        "dd MMM yyyy, hh:mm a"
                );

        return value.format(
                formatter
        );
    }

    // =========================================================
    // REQUEST STRING
    // =========================================================

    private String getString(
            Map<String, Object> request,
            String key
    ) {

        if (request == null) {
            return "";
        }

        Object value =
                request.get(key);

        if (value == null) {
            return "";
        }

        return value
                .toString()
                .trim();
    }

    // =========================================================
    // SESSION RESPONSE
    // =========================================================

    private Map<String, Object>
    toResponse(
            StudySession session,
            StudyGroup group,
            Long currentUserId
    ) {

        Map<String, Object> response =
                new LinkedHashMap<>();

        response.put(
                "id",
                session.getId()
        );

        response.put(
                "studyGroupId",
                session.getStudyGroupId()
        );

        response.put(
                "groupName",
                group.getName()
        );

        response.put(
                "title",
                session.getTitle()
        );

        response.put(
                "agenda",
                session.getAgenda()
        );

        response.put(
                "scheduledAt",
                session.getScheduledAt()
        );

        response.put(
                "status",
                session.getStatus()
        );

        response.put(
                "createdBy",
                session.getCreatedBy()
        );

        response.put(
                "createdAt",
                session.getCreatedAt()
        );

        response.put(
                "startedAt",
                session.getStartedAt()
        );

        response.put(
                "endedAt",
                session.getEndedAt()
        );

        boolean canManage =
                currentUserId != null
                        &&
                group.getCreatorId()
                        .equals(currentUserId);

        response.put(
                "canManage",
                canManage
        );

        response.put(
                "roomUrl",
                "/study-hall/"
                        + group.getId()
                        + "/room"
        );

        return response;
    }
}