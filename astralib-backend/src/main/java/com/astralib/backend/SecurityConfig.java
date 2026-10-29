
package com.astralib.backend;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter
    ) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http
    ) throws Exception {

        http
                .csrf(csrf -> csrf.disable())

                .sessionManagement(session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )

                .authorizeHttpRequests(auth -> auth

                        // Public authentication endpoints
                        .requestMatchers(
                                "/api/auth/register",
                                "/api/auth/login"
                        ).permitAll()

                        // Public health and root endpoints
                        .requestMatchers(
                                "/",
                                "/error",
                                "/actuator/health"
                        ).permitAll()

                        // WebSocket handshake
                        .requestMatchers("/ws/study/**").permitAll()

                        // Public library endpoints
                        .requestMatchers("/api/authors/**").permitAll()
                        .requestMatchers("/api/categories/**").permitAll()
                        .requestMatchers("/api/books/**").permitAll()
                        .requestMatchers("/api/resources/**").permitAll()
                        .requestMatchers("/api/favorites/**").permitAll()
                        .requestMatchers("/api/reading-history/**").permitAll()

                        // Recommendation endpoints
                        .requestMatchers("/api/recommendations/**")
                        .hasAnyRole("USER", "LIBRARIAN", "ADMIN")

                        .requestMatchers("/api/user/recommendations")
                        .hasAnyRole("USER", "LIBRARIAN", "ADMIN")

                        // User endpoints
                        .requestMatchers("/api/user/**")
                        .hasAnyRole("USER", "LIBRARIAN", "ADMIN")

                        .requestMatchers("/api/users/**")
                        .hasAnyRole("USER", "LIBRARIAN", "ADMIN")

                        // Search and RAG endpoints
                        .requestMatchers("/api/search/**")
                        .hasAnyRole("USER", "LIBRARIAN", "ADMIN")

                        .requestMatchers("/api/rag/**")
                        .hasAnyRole("USER", "LIBRARIAN", "ADMIN")

                        // Admin endpoints
                        .requestMatchers("/api/admin/**")
                        .hasRole("ADMIN")

                        // Librarian endpoints
                        .requestMatchers("/api/librarian/**")
                        .hasAnyRole("LIBRARIAN", "ADMIN")

                        // All other endpoints require authentication
                        .anyRequest().authenticated()
                )

                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
                );
                 System.out.println(
                "DEBUG: Updated SecurityConfig is being loaded"
        );

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService(
            UserRepository userRepository
    ) {
        return email ->
                userRepository.findByEmail(email)
                        .map(user ->
                                org.springframework.security.core.userdetails.User
                                        .withUsername(user.getEmail())
                                        .password(user.getPassword())
                                        .roles(user.getRole().name())
                                        .build()
                        )
                        .orElseThrow(() ->
                                new UsernameNotFoundException(
                                        "User not found"
                                )
                        );
    }
}
