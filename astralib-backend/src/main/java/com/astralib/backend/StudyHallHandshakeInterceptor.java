package com.astralib.backend;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
public class StudyHallHandshakeInterceptor
        implements HandshakeInterceptor {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public StudyHallHandshakeInterceptor(
            JwtService jwtService,
            UserRepository userRepository
    ) {
        this.jwtService =
                jwtService;

        this.userRepository =
                userRepository;
    }

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes
    ) {

        try {

            String token =
                    extractToken(
                            request.getURI()
                    );

            if (
                    token == null
                            ||
                    token.isBlank()
            ) {

                System.out.println(
                        "Study Hall WebSocket rejected: token missing"
                );

                return false;
            }

            if (
                    !jwtService
                            .validateToken(token)
            ) {

                System.out.println(
                        "Study Hall WebSocket rejected: invalid JWT"
                );

                return false;
            }

            Long userId =
                    jwtService
                            .extractUserId(token);

            if (
                    !userRepository
                            .existsById(userId)
            ) {

                System.out.println(
                        "Study Hall WebSocket rejected: user not found"
                );

                return false;
            }

            attributes.put(
                    "userId",
                    userId
            );

            /*
             * Do NOT store the JWT itself
             * in WebSocket attributes.
             */

            System.out.println(
                    "Study Hall WebSocket authenticated user "
                            + userId
            );

            return true;

        } catch (Exception e) {

            System.out.println(
                    "Study Hall WebSocket handshake failed: "
                            + e.getMessage()
            );

            return false;
        }
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception
    ) {
        // Nothing required here.
    }

    private String extractToken(
            URI uri
    ) {

        String query =
                uri.getRawQuery();

        if (
                query == null
                        ||
                query.isBlank()
        ) {
            return null;
        }

        String[] params =
                query.split("&");

        for (
                String param : params
        ) {

            String[] pair =
                    param.split(
                            "=",
                            2
                    );

            if (
                    pair.length == 2
                            &&
                    pair[0]
                            .equals("token")
            ) {

                return URLDecoder.decode(
                        pair[1],
                        StandardCharsets.UTF_8
                );
            }
        }

        return null;
    }
}