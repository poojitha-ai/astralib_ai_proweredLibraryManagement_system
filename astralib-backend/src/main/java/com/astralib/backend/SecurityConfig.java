
package com.astralib.backend;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
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

    /*
     * Catalogue resources that are publicly readable but may only be
     * modified by a librarian or administrator.
     */
    private static final String[] CATALOGUE_PATHS = {
            "/api/books/**",
            "/api/authors/**",
            "/api/categories/**",
            "/api/resources/**"
    };

    /*
     * Curated pathways are part of catalogue browsing: public to read,
     * managed only through the admin curation endpoint.
     */
    private static final String[] PATHWAY_PATHS = {
            "/api/pathways",
            "/api/pathways/**"
    };

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

                        // -----------------------------------------
                        // Public catalogue browsing (read-only)
                        //
                        // Anyone may READ the catalogue and the
                        // publicly served book files. Writes are
                        // restricted below, not here.
                        // -----------------------------------------
                        .requestMatchers(
                                HttpMethod.GET,
                                CATALOGUE_PATHS
                        ).permitAll()

                        .requestMatchers(
                                HttpMethod.HEAD,
                                CATALOGUE_PATHS
                        ).permitAll()

                        // -----------------------------------------
                        // Public pathway browsing (read-only)
                        // -----------------------------------------
                        .requestMatchers(
                                HttpMethod.GET,
                                PATHWAY_PATHS
                        ).permitAll()

                        .requestMatchers(
                                HttpMethod.HEAD,
                                PATHWAY_PATHS
                        ).permitAll()

                        // -----------------------------------------
                        // Catalogue management (writes)
                        //
                        // Creating, editing, deleting or uploading
                        // books, authors, categories and digital
                        // resources is a librarian/admin operation.
                        // -----------------------------------------
                        .requestMatchers(
                                HttpMethod.POST,
                                CATALOGUE_PATHS
                        ).hasAnyRole("LIBRARIAN", "ADMIN")

                        .requestMatchers(
                                HttpMethod.PUT,
                                CATALOGUE_PATHS
                        ).hasAnyRole("LIBRARIAN", "ADMIN")

                        .requestMatchers(
                                HttpMethod.PATCH,
                                CATALOGUE_PATHS
                        ).hasAnyRole("LIBRARIAN", "ADMIN")

                        .requestMatchers(
                                HttpMethod.DELETE,
                                CATALOGUE_PATHS
                        ).hasAnyRole("LIBRARIAN", "ADMIN")

                        // -----------------------------------------
                        // Per-user library data
                        //
                        // Favorites and reading history belong to
                        // the authenticated user. They must never
                        // be reachable anonymously, and the
                        // controllers scope every query to the
                        // JWT principal (no user id is accepted
                        // from the request).
                        // -----------------------------------------
                        .requestMatchers(
                                "/api/favorites/**",
                                "/api/reading-history/**"
                        ).hasAnyRole("USER", "LIBRARIAN", "ADMIN")

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

                        // Conversational Research Assistant. Chats belong to
                        // the signed-in reader; the controller takes the
                        // user id from the JWT, never from the request.
                        .requestMatchers("/api/conversations/**")
                        .hasAnyRole("USER", "LIBRARIAN", "ADMIN")

                        // Private student document uploads. Strictly scoped to
                        // the authenticated owner.
                        .requestMatchers("/api/student-documents/**")
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
