
package com.astralib.backend;

import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class StudySessionTrackingService {

    private final PersonalStudySessionRepository sessionRepository;
    private final StudySessionSegmentRepository segmentRepository;
    private final UserRepository userRepository;
    private final StudyPlanRepository planRepository;
    private final StudyPlanTaskRepository taskRepository;
    private final StudyGroupMemberRepository memberRepository;
    private final StudySessionRepository studySessionRepository;

    public StudySessionTrackingService(
            PersonalStudySessionRepository sessionRepository,
            StudySessionSegmentRepository segmentRepository,
            UserRepository userRepository,
            StudyPlanRepository planRepository,
            StudyPlanTaskRepository taskRepository,
            StudyGroupMemberRepository memberRepository,
            StudySessionRepository studySessionRepository
    ) {
        this.sessionRepository = sessionRepository;
        this.segmentRepository = segmentRepository;
        this.userRepository = userRepository;
        this.planRepository = planRepository;
        this.taskRepository = taskRepository;
        this.memberRepository = memberRepository;
        this.studySessionRepository = studySessionRepository;
    }

    // START A STUDY SESSION
    @Transactional
    public Map<String, Object> startSession(
            Long userId,
            Map<String, Object> request
    ) {
        userRepository.findByIdForUpdate(userId)
                .orElseThrow(() ->
                        new IllegalArgumentException("User not found"));

        if (sessionRepository.findFirstByUserIdAndStatusInOrderByStartedAtDesc(
                userId, List.of("ACTIVE", "PAUSED")
        ).isPresent()) {
            throw new IllegalArgumentException(
                    "You already have an unfinished study session"
            );
        }

        String source = requiredText(request, "source").toUpperCase();

        if (!source.equals("PERSONAL_TIMER")
                && !source.equals("STUDY_HALL")) {
            throw new IllegalArgumentException(
                    "Invalid study session source"
            );
        }

        String title = optionalText(request, "title");
        String subject = optionalText(request, "subject");

        Long planTaskId = optionalLong(request, "planTaskId");

        if (planTaskId != null) {
            StudyPlanTask task = taskRepository.findById(planTaskId)
                    .orElseThrow(() ->
                            new IllegalArgumentException("Study task not found"));

            planRepository.findByIdAndUserId(task.getPlanId(), userId)
                    .orElseThrow(() ->
                            new IllegalArgumentException(
                                    "Study task does not belong to your plan"
                            ));

            if (title == null) {
                title = task.getTitle();
            }

            if (subject == null) {
                subject = task.getSubject();
            }
        }

        if (title == null) {
            title = "Study Session";
        }

        if (subject == null) {
            throw new IllegalArgumentException("Subject is required");
        }

        Long groupId = null;
        Long hallSessionId = null;

        if (source.equals("STUDY_HALL")) {

            groupId = requiredLong(request, "studyGroupId");
            hallSessionId = requiredLong(request, "studyHallSessionId");

            if (!memberRepository.existsByStudyGroupIdAndUserId(
                    groupId, userId
            )) {
                throw new IllegalArgumentException(
                        "You are not a member of this study group"
                );
            }

            StudySession hallSession = studySessionRepository.findById(
                    hallSessionId
            ).orElseThrow(() ->
                    new IllegalArgumentException(
                            "Study Hall session not found"
                    ));

            if (!Objects.equals(hallSession.getStudyGroupId(), groupId)
                    || !"LIVE".equals(hallSession.getStatus())) {
                throw new IllegalArgumentException(
                        "The Study Hall session is not live"
                );
            }
        }

        LocalDateTime now = LocalDateTime.now();

        PersonalStudySession session = new PersonalStudySession();

        session.setUserId(userId);
        session.setPlanTaskId(planTaskId);
        session.setTitle(title);
        session.setSubject(subject);
        session.setSource(source);
        session.setStudyGroupId(groupId);
        session.setStudyHallSessionId(hallSessionId);
        session.setStatus("ACTIVE");
        session.setStartedAt(now);
        session.setActiveSegmentStartedAt(now);
        session.setAccumulatedSeconds(0L);

        sessionRepository.save(session);

        startNewSegment(session, now);

        return toResponse(session);
    }

    // PAUSE A STUDY SESSION
    @Transactional
    public Map<String, Object> pauseSession(
            Long userId,
            Long sessionId
    ) {
        PersonalStudySession session =
                getLockedSession(userId, sessionId);

        if (!"ACTIVE".equals(session.getStatus())) {
            throw new IllegalArgumentException(
                    "Only active sessions can be paused"
            );
        }

        LocalDateTime now = LocalDateTime.now();

        closeActiveSegment(session, now);

        session.setActiveSegmentStartedAt(null);
        session.setStatus("PAUSED");

        sessionRepository.save(session);

        return toResponse(session);
    }

    // RESUME A PAUSED SESSION
    @Transactional
    public Map<String, Object> resumeSession(
            Long userId,
            Long sessionId
    ) {
        userRepository.findByIdForUpdate(userId)
                .orElseThrow(() ->
                        new IllegalArgumentException("User not found"));

        PersonalStudySession session =
                getLockedSession(userId, sessionId);

        if (!"PAUSED".equals(session.getStatus())) {
            throw new IllegalArgumentException(
                    "Only paused sessions can be resumed"
            );
        }

        LocalDateTime now = LocalDateTime.now();

        session.setStatus("ACTIVE");
        session.setActiveSegmentStartedAt(now);

        sessionRepository.save(session);

        startNewSegment(session, now);

        return toResponse(session);
    }

    // FINISH A STUDY SESSION
    @Transactional
    public Map<String, Object> finishSession(
            Long userId,
            Long sessionId
    ) {
        userRepository.findByIdForUpdate(userId)
                .orElseThrow(() ->
                        new IllegalArgumentException("User not found"));

        PersonalStudySession session =
                getLockedSession(userId, sessionId);

        if (!"ACTIVE".equals(session.getStatus())
                && !"PAUSED".equals(session.getStatus())) {
            throw new IllegalArgumentException(
                    "Session is already finished"
            );
        }

        LocalDateTime now = LocalDateTime.now();

        if ("ACTIVE".equals(session.getStatus())) {
            closeActiveSegment(session, now);
        }

        session.setStatus("COMPLETED");
        session.setActiveSegmentStartedAt(null);
        session.setEndedAt(now);

        sessionRepository.save(session);

        return toResponse(session);
    }

    // GET CURRENT SESSION
    public Map<String, Object> getCurrentSession(Long userId) {
        return sessionRepository
                .findFirstByUserIdAndStatusInOrderByStartedAtDesc(
                        userId,
                        List.of("ACTIVE", "PAUSED")
                )
                .map(this::toResponse)
                .orElse(null);
    }

    // GET STUDY HISTORY
    public List<Map<String, Object>> getHistory(Long userId) {
        return sessionRepository
                .findByUserIdOrderByStartedAtDesc(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // CREATE A NEW ACTIVE SEGMENT
    private void startNewSegment(
            PersonalStudySession session,
            LocalDateTime startTime
    ) {
        StudySessionSegment segment = new StudySessionSegment();

        segment.setPersonalStudySessionId(session.getId());
        segment.setSegmentStartedAt(startTime);
        segment.setDurationSeconds(0L);

        segmentRepository.save(segment);
    }

    // CLOSE THE CURRENT ACTIVE SEGMENT
    private void closeActiveSegment(
            PersonalStudySession session,
            LocalDateTime endTime
    ) {
        StudySessionSegment segment = segmentRepository
                .findFirstByPersonalStudySessionIdAndSegmentEndedAtIsNullOrderBySegmentStartedAtDesc(
                        session.getId()
                )
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Active study segment not found"
                        ));

        long seconds = Math.max(
                0,
                Duration.between(
                        segment.getSegmentStartedAt(),
                        endTime
                ).getSeconds()
        );

        segment.setSegmentEndedAt(endTime);
        segment.setDurationSeconds(seconds);

        segmentRepository.save(segment);

        session.setAccumulatedSeconds(
                session.getAccumulatedSeconds() + seconds
        );
    }

    // LOCKED SESSION RETRIEVAL
    private PersonalStudySession getLockedSession(
            Long userId,
            Long sessionId
    ) {
        return sessionRepository
                .findByIdAndUserIdForUpdate(sessionId, userId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Study session not found"
                        ));
    }

    // BUILD API RESPONSE
    private Map<String, Object> toResponse(
            PersonalStudySession session
    ) {
        Map<String, Object> result = new LinkedHashMap<>();

        long totalSeconds = session.getAccumulatedSeconds();

        if ("ACTIVE".equals(session.getStatus())
                && session.getActiveSegmentStartedAt() != null) {

            totalSeconds += Math.max(
                    0,
                    Duration.between(
                            session.getActiveSegmentStartedAt(),
                            LocalDateTime.now()
                    ).getSeconds()
            );
        }

        result.put("id", session.getId());
        result.put("title", session.getTitle());
        result.put("subject", session.getSubject());
        result.put("source", session.getSource());
        result.put("status", session.getStatus());
        result.put("planTaskId", session.getPlanTaskId());
        result.put("studyGroupId", session.getStudyGroupId());
        result.put("studyHallSessionId", session.getStudyHallSessionId());
        result.put("startedAt", session.getStartedAt());
        result.put("endedAt", session.getEndedAt());
        result.put("durationSeconds", totalSeconds);
        result.put("durationMinutes", totalSeconds / 60);

        return result;
    }

    // REQUEST VALIDATION HELPERS
    private String requiredText(
            Map<String, Object> request,
            String key
    ) {
        String value = optionalText(request, key);

        if (value == null) {
            throw new IllegalArgumentException(
                    key + " is required"
            );
        }

        return value;
    }

    private String optionalText(
            Map<String, Object> request,
            String key
    ) {
        Object value = request.get(key);

        if (value == null) {
            return null;
        }

        String text = value.toString().trim();

        return text.isEmpty() ? null : text;
    }

    private Long requiredLong(
            Map<String, Object> request,
            String key
    ) {
        Long value = optionalLong(request, key);

        if (value == null) {
            throw new IllegalArgumentException(
                    key + " is required"
            );
        }

        return value;
    }

    private Long optionalLong(
            Map<String, Object> request,
            String key
    ) {
        Object value = request.get(key);

        if (value == null) {
            return null;
        }

        try {
            return Long.valueOf(value.toString());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    key + " must be a valid number"
            );
        }
    }
}
