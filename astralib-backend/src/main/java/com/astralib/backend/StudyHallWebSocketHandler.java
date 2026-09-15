package com.astralib.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Component;

import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class StudyHallWebSocketHandler
        extends TextWebSocketHandler {

    private final ObjectMapper objectMapper =
            new ObjectMapper();

    private final StudyGroupRepository
            studyGroupRepository;

    private final StudyGroupMemberRepository
            studyGroupMemberRepository;

    private final UserRepository
            userRepository;

    private final Map<
            Long,
            Map<Long, WebSocketSession>
            > roomSessions =
            new ConcurrentHashMap<>();

    private final Map<String, Long>
            sessionGroups =
            new ConcurrentHashMap<>();

    public StudyHallWebSocketHandler(
            StudyGroupRepository
                    studyGroupRepository,

            StudyGroupMemberRepository
                    studyGroupMemberRepository,

            UserRepository
                    userRepository
    ) {

        this.studyGroupRepository =
                studyGroupRepository;

        this.studyGroupMemberRepository =
                studyGroupMemberRepository;

        this.userRepository =
                userRepository;
    }

    // =========================================================
    // SOCKET CONNECTED
    // =========================================================

    @Override
    public void afterConnectionEstablished(
            WebSocketSession session
    ) throws Exception {

        Long userId =
                getUserId(session);

        System.out.println(
                "Study Hall socket connected: user "
                        + userId
        );

        Map<String, Object> response =
                new LinkedHashMap<>();

        response.put(
                "type",
                "CONNECTED"
        );

        response.put(
                "userId",
                userId
        );

        send(
                session,
                response
        );
    }

    // =========================================================
    // MESSAGE
    // =========================================================

    @Override
    protected void handleTextMessage(
            WebSocketSession session,
            TextMessage message
    ) throws Exception {

        JsonNode root =
                objectMapper
                        .readTree(
                                message.getPayload()
                        );

        String type =
                getText(
                        root,
                        "type"
                );

        if (
                type == null
                        ||
                type.isBlank()
        ) {

            sendError(
                    session,
                    "Message type is required"
            );

            return;
        }

        switch (
                type.toUpperCase()
        ) {

            case "JOIN" ->
                    handleJoin(
                            session,
                            root
                    );

            case "LEAVE" ->
                    handleLeave(
                            session
                    );

            case "OFFER",
                 "ANSWER",
                 "ICE_CANDIDATE" ->
                    handleSignal(
                            session,
                            root,
                            type.toUpperCase()
                    );

            case "CHAT" ->
                    handleChat(
                            session,
                            root
                    );

            default ->
                    sendError(
                            session,
                            "Unsupported message type: "
                                    + type
                    );
        }
    }

    // =========================================================
    // JOIN ROOM
    // =========================================================

    private void handleJoin(
            WebSocketSession session,
            JsonNode root
    ) throws Exception {

        Long userId =
                getUserId(session);

        Long groupId =
                getLong(
                        root,
                        "groupId"
                );

        if (groupId == null) {

            sendError(
                    session,
                    "groupId is required"
            );

            return;
        }

        StudyGroup group =
                studyGroupRepository
                        .findById(groupId)
                        .orElse(null);

        if (group == null) {

            sendError(
                    session,
                    "Study group not found"
            );

            return;
        }

        boolean member =
                studyGroupMemberRepository
                        .existsByStudyGroupIdAndUserId(
                                groupId,
                                userId
                        );

        if (!member) {

            sendError(
                    session,
                    "You must join the study group before entering the live room"
            );

            return;
        }

        /*
         * If this session had already joined another
         * room, remove it from that room first.
         */
        removeFromCurrentRoom(
                session,
                false
        );

        Map<Long, WebSocketSession>
                groupSessions =
                roomSessions
                        .computeIfAbsent(
                                groupId,
                                ignored ->
                                        new ConcurrentHashMap<>()
                        );

        /*
         * Capture existing users BEFORE adding
         * the new participant.
         */
        List<Long> existingUsers =
                new ArrayList<>(
                        groupSessions
                                .keySet()
                );

        groupSessions.put(
                userId,
                session
        );

        sessionGroups.put(
                session.getId(),
                groupId
        );

        /*
         * Tell the joining user who is already
         * inside the room.
         */
        Map<String, Object> joined =
                new LinkedHashMap<>();

        joined.put(
                "type",
                "JOINED"
        );

        joined.put(
                "groupId",
                groupId
        );

        joined.put(
                "userId",
                userId
        );

        joined.put(
                "existingUsers",
                existingUsers
        );

        joined.put(
                "participants",
                buildParticipantList(
                        groupId
                )
        );

        send(
                session,
                joined
        );

        /*
         * Tell existing participants that
         * someone new entered.
         */
        Map<String, Object> participantJoined =
                new LinkedHashMap<>();

        participantJoined.put(
                "type",
                "PARTICIPANT_JOINED"
        );

        participantJoined.put(
                "groupId",
                groupId
        );

        participantJoined.put(
                "userId",
                userId
        );

        userRepository
                .findById(userId)
                .ifPresent(
                        user ->
                                participantJoined.put(
                                        "name",
                                        user.getName()
                                )
                );

        broadcastExcept(
                groupId,
                userId,
                participantJoined
        );

        broadcastParticipants(
                groupId
        );

        System.out.println(
                "User "
                        + userId
                        + " joined live Study Hall room "
                        + groupId
        );
    }

    // =========================================================
    // WEBRTC SIGNALING
    // =========================================================

    private void handleSignal(
            WebSocketSession session,
            JsonNode root,
            String type
    ) throws Exception {

        Long senderUserId =
                getUserId(session);

        Long groupId =
                sessionGroups
                        .get(
                                session.getId()
                        );

        if (groupId == null) {

            sendError(
                    session,
                    "Join a Study Hall room before sending WebRTC signals"
            );

            return;
        }

        Long targetUserId =
                getLong(
                        root,
                        "targetUserId"
                );

        if (targetUserId == null) {

            sendError(
                    session,
                    "targetUserId is required"
            );

            return;
        }

        Map<String, Object> forwarded =
                new LinkedHashMap<>();

        forwarded.put(
                "type",
                type
        );

        forwarded.put(
                "groupId",
                groupId
        );

        forwarded.put(
                "fromUserId",
                senderUserId
        );

        forwarded.put(
                "targetUserId",
                targetUserId
        );

        if (
                root.has(
                        "payload"
                )
        ) {

            forwarded.put(
                    "payload",
                    objectMapper
                            .convertValue(
                                    root.get(
                                            "payload"
                                    ),
                                    Object.class
                            )
            );
        }

        sendToUser(
                groupId,
                targetUserId,
                forwarded
        );
    }

    // =========================================================
    // CHAT
    // =========================================================

    private void handleChat(
            WebSocketSession session,
            JsonNode root
    ) throws Exception {

        Long userId =
                getUserId(session);

        Long groupId =
                sessionGroups
                        .get(
                                session.getId()
                        );

        if (groupId == null) {

            sendError(
                    session,
                    "Join a Study Hall room before chatting"
            );

            return;
        }

        String content =
                getText(
                        root,
                        "content"
                );

        if (
                content == null
                        ||
                content.isBlank()
        ) {

            sendError(
                    session,
                    "Chat message cannot be empty"
            );

            return;
        }

        Map<String, Object> chat =
                new LinkedHashMap<>();

        chat.put(
                "type",
                "CHAT"
        );

        chat.put(
                "groupId",
                groupId
        );

        chat.put(
                "userId",
                userId
        );

        chat.put(
                "content",
                content.trim()
        );

        chat.put(
                "sentAt",
                java.time.LocalDateTime.now()
                        .toString()
        );

        userRepository
                .findById(userId)
                .ifPresent(
                        user ->
                                chat.put(
                                        "name",
                                        user.getName()
                                )
                );

        broadcast(
                groupId,
                chat
        );
    }

    // =========================================================
    // LEAVE
    // =========================================================

    private void handleLeave(
            WebSocketSession session
    ) throws Exception {

        removeFromCurrentRoom(
                session,
                true
        );
    }

    // =========================================================
    // DISCONNECTED
    // =========================================================

    @Override
    public void afterConnectionClosed(
            WebSocketSession session,
            CloseStatus status
    ) throws Exception {

        removeFromCurrentRoom(
                session,
                true
        );

        System.out.println(
                "Study Hall socket disconnected: "
                        + session.getId()
        );
    }

    // =========================================================
    // REMOVE FROM ROOM
    // =========================================================

    private void removeFromCurrentRoom(
            WebSocketSession session,
            boolean notify
    ) throws Exception {

        Long groupId =
                sessionGroups
                        .remove(
                                session.getId()
                        );

        if (groupId == null) {
            return;
        }

        Long userId =
                getUserId(session);

        Map<Long, WebSocketSession>
                groupSessions =
                roomSessions
                        .get(groupId);

        if (
                groupSessions != null
        ) {

            groupSessions.remove(
                    userId
            );

            if (
                    groupSessions.isEmpty()
            ) {

                roomSessions.remove(
                        groupId
                );
            }
        }

        if (notify) {

            Map<String, Object> left =
                    new LinkedHashMap<>();

            left.put(
                    "type",
                    "PARTICIPANT_LEFT"
            );

            left.put(
                    "groupId",
                    groupId
            );

            left.put(
                    "userId",
                    userId
            );

            broadcast(
                    groupId,
                    left
            );

            broadcastParticipants(
                    groupId
            );
        }
    }

    // =========================================================
    // PARTICIPANT LIST
    // =========================================================

    private List<Map<String, Object>>
    buildParticipantList(
            Long groupId
    ) {

        List<Map<String, Object>> result =
                new ArrayList<>();

        Map<Long, WebSocketSession>
                groupSessions =
                roomSessions
                        .get(groupId);

        if (
                groupSessions == null
        ) {
            return result;
        }

        for (
                Long userId :
                        groupSessions
                                .keySet()
        ) {

            Map<String, Object> participant =
                    new LinkedHashMap<>();

            participant.put(
                    "userId",
                    userId
            );

            userRepository
                    .findById(userId)
                    .ifPresent(
                            user -> {
                                participant.put(
                                        "name",
                                        user.getName()
                                );

                                participant.put(
                                        "role",
                                        user.getRole()
                                );
                            }
                    );

            result.add(
                    participant
            );
        }

        return result;
    }

    private void broadcastParticipants(
            Long groupId
    ) throws Exception {

        Map<String, Object> response =
                new LinkedHashMap<>();

        response.put(
                "type",
                "PARTICIPANTS"
        );

        response.put(
                "groupId",
                groupId
        );

        response.put(
                "participants",
                buildParticipantList(
                        groupId
                )
        );

        broadcast(
                groupId,
                response
        );
    }

    // =========================================================
    // SEND HELPERS
    // =========================================================

    private void sendToUser(
            Long groupId,
            Long targetUserId,
            Map<String, Object> payload
    ) throws Exception {

        Map<Long, WebSocketSession>
                groupSessions =
                roomSessions
                        .get(groupId);

        if (
                groupSessions == null
        ) {
            return;
        }

        WebSocketSession target =
                groupSessions
                        .get(
                                targetUserId
                        );

        if (
                target != null
                        &&
                target.isOpen()
        ) {

            send(
                    target,
                    payload
            );
        }
    }

    private void broadcast(
            Long groupId,
            Map<String, Object> payload
    ) throws Exception {

        Map<Long, WebSocketSession>
                groupSessions =
                roomSessions
                        .get(groupId);

        if (
                groupSessions == null
        ) {
            return;
        }

        for (
                WebSocketSession session :
                        groupSessions.values()
        ) {

            if (
                    session.isOpen()
            ) {

                send(
                        session,
                        payload
                );
            }
        }
    }

    private void broadcastExcept(
            Long groupId,
            Long excludedUserId,
            Map<String, Object> payload
    ) throws Exception {

        Map<Long, WebSocketSession>
                groupSessions =
                roomSessions
                        .get(groupId);

        if (
                groupSessions == null
        ) {
            return;
        }

        for (
                Map.Entry<
                        Long,
                        WebSocketSession
                        > entry :
                        groupSessions
                                .entrySet()
        ) {

            if (
                    entry.getKey()
                            .equals(
                                    excludedUserId
                            )
            ) {
                continue;
            }

            WebSocketSession session =
                    entry.getValue();

            if (
                    session.isOpen()
            ) {

                send(
                        session,
                        payload
                );
            }
        }
    }

    private void send(
            WebSocketSession session,
            Map<String, Object> payload
    ) throws Exception {

        String json =
                objectMapper
                        .writeValueAsString(
                                payload
                        );

        synchronized (session) {

            if (
                    session.isOpen()
            ) {

                session.sendMessage(
                        new TextMessage(
                                json
                        )
                );
            }
        }
    }

    private void sendError(
            WebSocketSession session,
            String message
    ) throws Exception {

        Map<String, Object> response =
                new LinkedHashMap<>();

        response.put(
                "type",
                "ERROR"
        );

        response.put(
                "message",
                message
        );

        send(
                session,
                response
        );
    }

    // =========================================================
    // HELPERS
    // =========================================================

    private Long getUserId(
            WebSocketSession session
    ) {

        Object value =
                session
                        .getAttributes()
                        .get("userId");

        if (
                value instanceof Long
        ) {
            return (Long) value;
        }

        return Long.valueOf(
                value.toString()
        );
    }

    private String getText(
            JsonNode root,
            String field
    ) {

        JsonNode node =
                root.get(field);

        if (
                node == null
                        ||
                node.isNull()
        ) {
            return null;
        }

        return node.asText();
    }

    private Long getLong(
            JsonNode root,
            String field
    ) {

        JsonNode node =
                root.get(field);

        if (
                node == null
                        ||
                node.isNull()
        ) {
            return null;
        }

        if (
                node.isNumber()
        ) {
            return node.asLong();
        }

        try {

            return Long.valueOf(
                    node.asText()
            );

        } catch (
                NumberFormatException e
        ) {

            return null;
        }
    }
}