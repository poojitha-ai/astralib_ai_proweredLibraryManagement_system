package com.astralib.backend;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/saved-research")
public class SavedResearchController {

    @Autowired
    private SavedResearchService savedResearchService;

    // =========================================================
    // SAVE ANSWER
    // =========================================================

    @PostMapping
    public ResponseEntity<?> save(
            Authentication authentication,
            @RequestBody Map<String, Object> request
    ) {

        try {

            return ResponseEntity
                    .status(
                            HttpStatus.CREATED
                    )
                    .body(
                            savedResearchService.save(
                                    getUserId(
                                            authentication
                                    ),
                                    request
                            )
                    );

        } catch (IllegalArgumentException e) {

            return ResponseEntity
                    .badRequest()
                    .body(
                            error(
                                    e.getMessage()
                            )
                    );
        }
    }

    // =========================================================
    // MY RESEARCH
    // =========================================================

    @GetMapping
    public ResponseEntity<?> getAll(
            Authentication authentication
    ) {

        try {

            return ResponseEntity.ok(
                    savedResearchService.getAll(
                            getUserId(
                                    authentication
                            )
                    )
            );

        } catch (IllegalArgumentException e) {

            return ResponseEntity
                    .badRequest()
                    .body(
                            error(
                                    e.getMessage()
                            )
                    );
        }
    }

    // =========================================================
    // OPEN ONE SAVED ITEM
    // =========================================================

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(
            Authentication authentication,
            @PathVariable Long id
    ) {

        try {

            return ResponseEntity.ok(
                    savedResearchService.getById(
                            getUserId(
                                    authentication
                            ),
                            id
                    )
            );

        } catch (IllegalArgumentException e) {

            return ResponseEntity
                    .status(
                            HttpStatus.NOT_FOUND
                    )
                    .body(
                            error(
                                    e.getMessage()
                            )
                    );
        }
    }

    // =========================================================
    // DELETE ONE SAVED ITEM
    // =========================================================

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(
            Authentication authentication,
            @PathVariable Long id
    ) {

        try {

            savedResearchService.delete(
                    getUserId(
                            authentication
                    ),
                    id
            );

            Map<String, Object> response =
                    new LinkedHashMap<>();

            response.put(
                    "message",
                    "Saved research deleted successfully"
            );

            response.put(
                    "id",
                    id
            );

            return ResponseEntity.ok(
                    response
            );

        } catch (IllegalArgumentException e) {

            return ResponseEntity
                    .status(
                            HttpStatus.NOT_FOUND
                    )
                    .body(
                            error(
                                    e.getMessage()
                            )
                    );
        }
    }

    // =========================================================
    // AUTH USER ID
    // =========================================================

    private Long getUserId(
            Authentication authentication
    ) {

        if (
                authentication == null
                        || !authentication.isAuthenticated()
        ) {

            throw new IllegalArgumentException(
                    "Authenticated user is required"
            );
        }

        Object principal =
                authentication.getPrincipal();

        if (principal instanceof Long) {
            return (Long) principal;
        }

        if (principal instanceof Integer) {
            return ((Integer) principal).longValue();
        }

        if (principal != null) {

            try {
                return Long.parseLong(
                        principal.toString()
                );
            } catch (NumberFormatException ignored) {
            }
        }

        throw new IllegalArgumentException(
                "Authenticated user ID is invalid"
        );
    }

    // =========================================================
    // ERROR RESPONSE
    // =========================================================

    private Map<String, Object> error(
            String message
    ) {

        Map<String, Object> response =
                new LinkedHashMap<>();

        response.put(
                "error",
                message
        );

        return response;
    }
}
