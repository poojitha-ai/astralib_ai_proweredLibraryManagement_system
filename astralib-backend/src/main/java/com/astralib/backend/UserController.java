package com.astralib.backend;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/users/me")
    public ResponseEntity<?> getCurrentUser(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        User user = userService.findById(userId);
        
        if (user == null) {
            return ResponseEntity.notFound().build();
        }

        Map<String, Object> resp = new java.util.LinkedHashMap<>();
        resp.put("id", user.getId());
        resp.put("name", user.getName() != null ? user.getName() : "");
        resp.put("email", user.getEmail());
        resp.put("role", user.getRole() != null ? user.getRole().name() : "USER");
        resp.put("createdAt", user.getCreatedAt() != null ? user.getCreatedAt().toString() : null);
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/user/test")
    public ResponseEntity<?> userTest() {
        return ResponseEntity.ok(Map.of("message", "User endpoint accessed successfully"));
    }

    @GetMapping("/librarian/test")
    public ResponseEntity<?> librarianTest() {
        return ResponseEntity.ok(Map.of("message", "Librarian endpoint accessed successfully"));
    }

    @GetMapping("/admin/test")
    public ResponseEntity<?> adminTest() {
        return ResponseEntity.ok(Map.of("message", "Admin endpoint accessed successfully"));
    }

    @PostMapping("/admin/update-role/{userId}")
    public ResponseEntity<?> updateUserRole(@PathVariable Long userId, @RequestParam String role) {
        try {
            Role newRole = Role.valueOf(role.toUpperCase());
            User user = userService.updateUserRole(userId, newRole);
            return ResponseEntity.ok(Map.of(
                    "id", user.getId(),
                    "email", user.getEmail(),
                    "role", user.getRole().name()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
