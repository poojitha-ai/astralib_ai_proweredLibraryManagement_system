
package com.astralib.backend;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/study-tracking")
public class StudySessionTrackingController {

    private final StudySessionTrackingService trackingService;

    public StudySessionTrackingController(
            StudySessionTrackingService trackingService
    ) {
        this.trackingService = trackingService;
    }

    @PostMapping("/start")
    public ResponseEntity<?> startSession(
            Authentication authentication,
            @RequestBody Map<String, Object> request
    ) {
        Long userId = CurrentUser.id(authentication);

        if (userId == null) {
            return CurrentUser.unauthorizedResponse();
        }

        try {
            return ResponseEntity.ok(
                    trackingService.startSession(userId, request)
            );
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", exception.getMessage()));
        }
    }

    @PostMapping("/{id}/pause")
    public ResponseEntity<?> pauseSession(
            Authentication authentication,
            @PathVariable Long id
    ) {
        Long userId = CurrentUser.id(authentication);

        if (userId == null) {
            return CurrentUser.unauthorizedResponse();
        }

        try {
            return ResponseEntity.ok(
                    trackingService.pauseSession(userId, id)
            );
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", exception.getMessage()));
        }
    }

    @PostMapping("/{id}/resume")
    public ResponseEntity<?> resumeSession(
            Authentication authentication,
            @PathVariable Long id
    ) {
        Long userId = CurrentUser.id(authentication);

        if (userId == null) {
            return CurrentUser.unauthorizedResponse();
        }

        try {
            return ResponseEntity.ok(
                    trackingService.resumeSession(userId, id)
            );
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", exception.getMessage()));
        }
    }

    @PostMapping("/{id}/finish")
    public ResponseEntity<?> finishSession(
            Authentication authentication,
            @PathVariable Long id
    ) {
        Long userId = CurrentUser.id(authentication);

        if (userId == null) {
            return CurrentUser.unauthorizedResponse();
        }

        try {
            return ResponseEntity.ok(
                    trackingService.finishSession(userId, id)
            );
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", exception.getMessage()));
        }
    }

    @GetMapping("/current")
    public ResponseEntity<?> getCurrentSession(
            Authentication authentication
    ) {
        Long userId = CurrentUser.id(authentication);

        if (userId == null) {
            return CurrentUser.unauthorizedResponse();
        }

        return ResponseEntity.ok(
                trackingService.getCurrentSession(userId)
        );
    }

    @GetMapping("/history")
    public ResponseEntity<?> getHistory(
            Authentication authentication
    ) {
        Long userId = CurrentUser.id(authentication);

        if (userId == null) {
            return CurrentUser.unauthorizedResponse();
        }

        return ResponseEntity.ok(
                trackingService.getHistory(userId)
        );
    }
}
