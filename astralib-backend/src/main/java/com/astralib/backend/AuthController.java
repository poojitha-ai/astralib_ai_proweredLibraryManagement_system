package com.astralib.backend;

import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;

    public AuthController(AuthService authService, UserService userService, PasswordEncoder passwordEncoder) {
        this.authService = authService;
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> request) {
        String name = request.get("name");
        String email = request.get("email");
        String password = request.get("password");

        if (name == null || email == null || password == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "All fields are required"));
        }

        try {
            User user = authService.register(name, email, password);
            return ResponseEntity.ok(Map.of("message", "Registration successful"));
        } catch (Exception e) {
            String errorMessage = (e.getMessage() != null && !e.getMessage().isBlank())
                    ? e.getMessage()
                    : "Registration failed";
            return ResponseEntity.badRequest().body(Map.of("error", errorMessage));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        String password = request.get("password");

        if (email == null || password == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email and password are required"));
        }

        try {
            String token = authService.login(email, password);
            User user = userService.findByEmail(email);
            Map<String, Object> response = new java.util.HashMap<>();
            response.put("token", token);
            if (user != null) {
                response.put("userId", user.getId());
                response.put("name", user.getName() != null ? user.getName() : "");
                response.put("role", user.getRole() != null ? user.getRole().name() : "USER");
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            String errorMessage = (e.getMessage() != null && !e.getMessage().isBlank())
                    ? e.getMessage()
                    : "Authentication failed. Invalid email or password.";
            return ResponseEntity.badRequest().body(Map.of("error", errorMessage));
        }
    }
}
