package com.astralib.backend;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/study-sessions")
public class StudySessionController {

    private final StudySessionService
            studySessionService;

    public StudySessionController(
            StudySessionService studySessionService
    ) {

        this.studySessionService =
                studySessionService;
    }

    // =========================================================
    // GET ALL SESSIONS FOR ONE GROUP
    // =========================================================

    @GetMapping("/group/{groupId}")
    public ResponseEntity<?> getGroupSessions(
            @PathVariable Long groupId,
            Authentication authentication
    ) {

        try {

            Long userId =
                    getUserId(
                            authentication
                    );

            return ResponseEntity.ok(
                    studySessionService
                            .getGroupSessions(
                                    groupId,
                                    userId
                            )
            );

        } catch (IllegalArgumentException e) {

            return badRequest(e);
        }
    }

    // =========================================================
    // GET LIVE / NEXT SESSION STATUS
    // =========================================================

    @GetMapping("/group/{groupId}/current")
    public ResponseEntity<?> getCurrentSessionStatus(
            @PathVariable Long groupId,
            Authentication authentication
    ) {

        try {

            Long userId =
                    getUserId(
                            authentication
                    );

            return ResponseEntity.ok(
                    studySessionService
                            .getCurrentSessionStatus(
                                    groupId,
                                    userId
                            )
            );

        } catch (IllegalArgumentException e) {

            return badRequest(e);
        }
    }

    // =========================================================
    // SCHEDULE A SESSION
    // =========================================================

    @PostMapping("/group/{groupId}/schedule")
    public ResponseEntity<?> scheduleSession(
            @PathVariable Long groupId,
            Authentication authentication,
            @RequestBody
            Map<String, Object> request
    ) {

        try {

            Long userId =
                    getUserId(
                            authentication
                    );

            Map<String, Object> session =
                    studySessionService
                            .scheduleSession(
                                    groupId,
                                    userId,
                                    request
                            );

            Map<String, Object> response =
                    new LinkedHashMap<>();

            response.put(
                    "message",
                    "Study session scheduled successfully"
            );

            response.put(
                    "session",
                    session
            );

            return ResponseEntity.ok(
                    response
            );

        } catch (IllegalArgumentException e) {

            return badRequest(e);
        }
    }

    // =========================================================
    // START NEW SESSION NOW
    // =========================================================

    @PostMapping("/group/{groupId}/start-now")
    public ResponseEntity<?> startNow(
            @PathVariable Long groupId,
            Authentication authentication,
            @RequestBody(required = false)
            Map<String, Object> request
    ) {

        try {

            Long userId =
                    getUserId(
                            authentication
                    );

            Map<String, Object> session =
                    studySessionService
                            .startNow(
                                    groupId,
                                    userId,
                                    request
                            );

            Map<String, Object> response =
                    new LinkedHashMap<>();

            response.put(
                    "message",
                    "Study discussion started"
            );

            response.put(
                    "session",
                    session
            );

            return ResponseEntity.ok(
                    response
            );

        } catch (IllegalArgumentException e) {

            return badRequest(e);
        }
    }

    // =========================================================
    // START AN EXISTING SCHEDULED SESSION
    // =========================================================

    @PostMapping("/{sessionId}/start")
    public ResponseEntity<?> startSession(
            @PathVariable Long sessionId,
            Authentication authentication
    ) {

        try {

            Long userId =
                    getUserId(
                            authentication
                    );

            Map<String, Object> session =
                    studySessionService
                            .startSession(
                                    sessionId,
                                    userId
                            );

            Map<String, Object> response =
                    new LinkedHashMap<>();

            response.put(
                    "message",
                    "Study discussion started"
            );

            response.put(
                    "session",
                    session
            );

            return ResponseEntity.ok(
                    response
            );

        } catch (IllegalArgumentException e) {

            return badRequest(e);
        }
    }

    // =========================================================
    // END LIVE SESSION
    // =========================================================

    @PostMapping("/{sessionId}/end")
    public ResponseEntity<?> endSession(
            @PathVariable Long sessionId,
            Authentication authentication
    ) {

        try {

            Long userId =
                    getUserId(
                            authentication
                    );

            Map<String, Object> session =
                    studySessionService
                            .endSession(
                                    sessionId,
                                    userId
                            );

            Map<String, Object> response =
                    new LinkedHashMap<>();

            response.put(
                    "message",
                    "Study session ended"
            );

            response.put(
                    "session",
                    session
            );

            return ResponseEntity.ok(
                    response
            );

        } catch (IllegalArgumentException e) {

            return badRequest(e);
        }
    }

    // =========================================================
    // CURRENT USER ID
    // =========================================================

    private Long getUserId(
            Authentication authentication
    ) {

        if (
                authentication == null
                        ||
                authentication.getPrincipal() == null
        ) {

            throw new IllegalArgumentException(
                    "Authenticated user not found"
            );
        }

        Object principal =
                authentication.getPrincipal();

        if (principal instanceof Long) {

            return (Long) principal;
        }

        try {

            return Long.valueOf(
                    principal.toString()
            );

        } catch (NumberFormatException e) {

            throw new IllegalArgumentException(
                    "Invalid authenticated user"
            );
        }
    }

    // =========================================================
    // ERROR RESPONSE
    // =========================================================

    private ResponseEntity<?> badRequest(
            IllegalArgumentException e
    ) {

        Map<String, Object> response =
                new LinkedHashMap<>();

        response.put(
                "error",
                e.getMessage()
        );

        return ResponseEntity
                .badRequest()
                .body(response);
    }
}