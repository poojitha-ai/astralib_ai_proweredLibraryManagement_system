package com.astralib.backend;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.util.Map;

/**
 * Reads the authenticated user id out of the security context safely.
 *
 * JwtAuthenticationFilter stores the numeric user id as the principal,
 * while an anonymous request carries the String "anonymousUser" (or no
 * Authentication at all). Casting the principal blindly therefore throws
 * a ClassCastException and turns an authentication problem into HTTP 500.
 */
final class CurrentUser {

    private CurrentUser() {
    }

    /**
     * @return the authenticated user id, or null when the request is not
     *         authenticated as an AstraLib user.
     */
    static Long id(Authentication authentication) {

        if (authentication == null) {
            return null;
        }

        Object principal = authentication.getPrincipal();

        return principal instanceof Long userId ? userId : null;
    }

    static ResponseEntity<?> unauthorizedResponse() {

        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Authentication required"));
    }
}
