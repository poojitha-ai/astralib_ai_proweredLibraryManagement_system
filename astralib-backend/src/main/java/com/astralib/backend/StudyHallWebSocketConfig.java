package com.astralib.backend;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class StudyHallWebSocketConfig
        implements WebSocketConfigurer {

    private final StudyHallWebSocketHandler
            studyHallWebSocketHandler;

    private final StudyHallHandshakeInterceptor
            studyHallHandshakeInterceptor;

    public StudyHallWebSocketConfig(
            StudyHallWebSocketHandler studyHallWebSocketHandler,
            StudyHallHandshakeInterceptor studyHallHandshakeInterceptor
    ) {

        this.studyHallWebSocketHandler =
                studyHallWebSocketHandler;

        this.studyHallHandshakeInterceptor =
                studyHallHandshakeInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(
            WebSocketHandlerRegistry registry
    ) {

        registry
                .addHandler(
                        studyHallWebSocketHandler,
                        "/ws/study"
                )
                .addInterceptors(
                        studyHallHandshakeInterceptor
                )
                /*
                 * Development / local-network testing.
                 *
                 * JWT authentication and group membership
                 * are still checked before users can enter
                 * Study Hall rooms.
                 *
                 * Restrict this for production deployment.
                 */
                .setAllowedOriginPatterns("*");
    }
}