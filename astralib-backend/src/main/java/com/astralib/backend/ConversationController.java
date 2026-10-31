package com.astralib.backend;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * The conversational Research Assistant.
 *
 * Every route is scoped to the authenticated user: no endpoint accepts a
 * user id, and a conversation belonging to someone else resolves as not
 * found rather than being readable.
 */
@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    @Autowired
    private ConversationService conversationService;

    // =========================================================
    // LIST / CREATE
    // =========================================================

    @GetMapping
    public ResponseEntity<?> list(Authentication authentication) {

        Long userId = CurrentUser.id(authentication);

        if (userId == null) {
            return CurrentUser.unauthorizedResponse();
        }

        return ResponseEntity.ok(conversationService.list(userId));
    }

    @PostMapping
    public ResponseEntity<?> create(Authentication authentication) {

        Long userId = CurrentUser.id(authentication);

        if (userId == null) {
            return CurrentUser.unauthorizedResponse();
        }

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(conversationService.create(userId));
    }

    // =========================================================
    // ONE CONVERSATION
    // =========================================================

    @GetMapping("/{id}")
    public ResponseEntity<?> get(
            Authentication authentication,
            @PathVariable Long id) {

        Long userId = CurrentUser.id(authentication);

        if (userId == null) {
            return CurrentUser.unauthorizedResponse();
        }

        try {
            return ResponseEntity.ok(conversationService.get(userId, id));
        } catch (ConversationService.ConversationNotFoundException e) {
            return notFound();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(
            Authentication authentication,
            @PathVariable Long id) {

        Long userId = CurrentUser.id(authentication);

        if (userId == null) {
            return CurrentUser.unauthorizedResponse();
        }

        try {
            conversationService.delete(userId, id);
            return ResponseEntity.ok(Map.of("deleted", true, "id", id));
        } catch (ConversationService.ConversationNotFoundException e) {
            return notFound();
        }
    }

    // =========================================================
    // SEND A MESSAGE
    // =========================================================

    @PostMapping("/messages")
    public ResponseEntity<?> sendWithoutConversation(
            Authentication authentication,
            @RequestBody Map<String, Object> request) {

        return send(authentication, null, request);
    }

    @PostMapping("/{id}/messages")
    public ResponseEntity<?> sendToConversation(
            Authentication authentication,
            @PathVariable Long id,
            @RequestBody Map<String, Object> request) {

        return send(authentication, id, request);
    }

    private ResponseEntity<?> send(
            Authentication authentication,
            Long conversationId,
            Map<String, Object> request) {

        Long userId = CurrentUser.id(authentication);

        if (userId == null) {
            return CurrentUser.unauthorizedResponse();
        }

        Object raw = request == null ? null : request.get("message");

        if (raw == null || raw.toString().isBlank()) {
            return ResponseEntity
                    .badRequest()
                    .body(Map.of("error", "A message is required."));
        }

        try {

            return ResponseEntity.ok(
                    conversationService.send(
                            userId,
                            conversationId,
                            raw.toString()
                    )
            );

        } catch (IllegalArgumentException e) {

            return ResponseEntity
                    .badRequest()
                    .body(Map.of("error", e.getMessage()));

        } catch (ConversationService.ConversationNotFoundException e) {

            return notFound();

        } catch (Exception e) {

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(
                            Map.of(
                                    "error",
                                    "The assistant could not answer: " + e.getMessage()
                            )
                    );
        }
    }

    private ResponseEntity<?> notFound() {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "Conversation not found."));
    }
}
