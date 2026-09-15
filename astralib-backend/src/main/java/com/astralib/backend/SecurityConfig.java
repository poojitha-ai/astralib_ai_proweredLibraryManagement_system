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
        this.jwtAuthenticationFilter =
                jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http
    ) throws Exception {

        http
                .csrf(csrf -> csrf.disable())

                .sessionManagement(
                        session ->
                                session.sessionCreationPolicy(
                                        SessionCreationPolicy.STATELESS
                                )
                )

                .authorizeHttpRequests(auth -> auth
        .requestMatchers(
                "/api/auth/register",
                "/api/auth/login"
        ).permitAll()

        // WebSocket handshake.
        // Authentication is performed by
        // StudyHallHandshakeInterceptor.
        .requestMatchers(
                "/ws/study/**"
        ).permitAll()

        .requestMatchers("/api/authors/**").permitAll()
        .requestMatchers("/api/categories/**").permitAll()
        .requestMatchers("/api/books/**").permitAll()
        .requestMatchers("/api/resources/**").permitAll()
        .requestMatchers("/api/favorites/**").permitAll()
        .requestMatchers("/api/reading-history/**").permitAll()

        .requestMatchers("/api/recommendations/**")
        .hasAnyRole("USER", "LIBRARIAN", "ADMIN")

        .requestMatchers("/api/user/recommendations")
        .hasAnyRole("USER", "LIBRARIAN", "ADMIN")

        .requestMatchers("/api/user/**")
        .hasAnyRole("USER", "LIBRARIAN", "ADMIN")

        .requestMatchers("/api/users/**")
        .hasAnyRole("USER", "LIBRARIAN", "ADMIN")

        .requestMatchers("/api/search/**")
        .hasAnyRole("USER", "LIBRARIAN", "ADMIN")

        .requestMatchers("/api/rag/**")
        .hasAnyRole("USER", "LIBRARIAN", "ADMIN")

        .requestMatchers("/api/admin/**")
        .hasRole("ADMIN")

        .requestMatchers("/api/librarian/**")
        .hasAnyRole("LIBRARIAN", "ADMIN")

        .anyRequest()
        .authenticated()
)

                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
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
                userRepository
                        .findByEmail(email)

                        .map(
                                user ->
                                        org.springframework.security
                                                .core
                                                .userdetails
                                                .User
                                                .withUsername(
                                                        user.getEmail()
                                                )
                                                .password(
                                                        user.getPassword()
                                                )
                                                .roles(
                                                        user.getRole()
                                                                .name()
                                                )
                                                .build()
                        )

                        .orElseThrow(
                                () ->
                                        new UsernameNotFoundException(
                                                "User not found"
                                        )
                        );
    }
}