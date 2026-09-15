package com.astralib.backend;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService
            notificationService;

    public NotificationController(
            NotificationService notificationService
    ) {
        this.notificationService =
                notificationService;
    }

    // =========================================================
    // GET MY NOTIFICATIONS
    // =========================================================

    @GetMapping
    public ResponseEntity<?> getMyNotifications(
            Authentication authentication
    ) {

        try {

            Long userId =
                    getUserId(authentication);

            return ResponseEntity.ok(
                    notificationService
                            .getMyNotifications(
                                    userId
                            )
            );

        } catch (IllegalArgumentException e) {

            return badRequest(e);
        }
    }

    // =========================================================
    // UNREAD COUNT
    // =========================================================

    @GetMapping("/unread-count")
    public ResponseEntity<?> getUnreadCount(
            Authentication authentication
    ) {

        try {

            Long userId =
                    getUserId(authentication);

            return ResponseEntity.ok(
                    notificationService
                            .getUnreadCount(
                                    userId
                            )
            );

        } catch (IllegalArgumentException e) {

            return badRequest(e);
        }
    }

    // =========================================================
    // MARK ONE READ
    // =========================================================

    @PutMapping("/{id}/read")
    public ResponseEntity<?> markAsRead(
            @PathVariable Long id,
            Authentication authentication
    ) {

        try {

            Long userId =
                    getUserId(authentication);

            return ResponseEntity.ok(
                    notificationService
                            .markAsRead(
                                    id,
                                    userId
                            )
            );

        } catch (IllegalArgumentException e) {

            return badRequest(e);
        }
    }

    // =========================================================
    // MARK ALL READ
    // =========================================================

    @PutMapping("/read-all")
    public ResponseEntity<?> markAllAsRead(
            Authentication authentication
    ) {

        try {

            Long userId =
                    getUserId(authentication);

            return ResponseEntity.ok(
                    notificationService
                            .markAllAsRead(
                                    userId
                            )
            );

        } catch (IllegalArgumentException e) {

            return badRequest(e);
        }
    }

    // =========================================================
    // CURRENT USER
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