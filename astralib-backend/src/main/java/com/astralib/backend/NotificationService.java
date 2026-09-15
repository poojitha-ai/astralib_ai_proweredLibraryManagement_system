package com.astralib.backend;

import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;

    private final StudyGroupMemberRepository
            studyGroupMemberRepository;

    public NotificationService(
            NotificationRepository notificationRepository,
            StudyGroupMemberRepository studyGroupMemberRepository
    ) {
        this.notificationRepository =
                notificationRepository;

        this.studyGroupMemberRepository =
                studyGroupMemberRepository;
    }

    // =========================================================
    // CREATE NOTIFICATIONS FOR GROUP MEMBERS
    // =========================================================

    @Transactional
    public void notifyGroupMembers(
            Long groupId,
            Long actorUserId,
            String type,
            String title,
            String message,
            Long sessionId
    ) {

        List<StudyGroupMember> memberships =
                studyGroupMemberRepository
                        .findByStudyGroupIdOrderByJoinedAtAsc(
                                groupId
                        );

        List<Notification> notifications =
                new ArrayList<>();

        for (StudyGroupMember membership : memberships) {

            Long memberUserId =
                    membership.getUserId();

            // Do not notify the person who performed
            // the action.
            if (
                    actorUserId != null
                            &&
                    actorUserId.equals(memberUserId)
            ) {
                continue;
            }

            Notification notification =
                    new Notification();

            notification.setUserId(
                    memberUserId
            );

            notification.setType(
                    type
            );

            notification.setTitle(
                    title
            );

            notification.setMessage(
                    message
            );

            notification.setStudyGroupId(
                    groupId
            );

            notification.setStudySessionId(
                    sessionId
            );

            notification.setTargetUrl(
                    "/study-hall/"
                            + groupId
                            + "/room"
            );

            notification.setReadFlag(
                    false
            );

            notifications.add(
                    notification
            );
        }

        if (!notifications.isEmpty()) {
            notificationRepository
                    .saveAll(notifications);
        }
    }

    // =========================================================
    // MY NOTIFICATIONS
    // =========================================================

    public List<Map<String, Object>>
    getMyNotifications(
            Long userId
    ) {

        List<Notification> notifications =
                notificationRepository
                        .findByUserIdOrderByCreatedAtDesc(
                                userId
                        );

        List<Map<String, Object>> result =
                new ArrayList<>();

        for (Notification notification : notifications) {
            result.add(
                    toResponse(notification)
            );
        }

        return result;
    }

    // =========================================================
    // UNREAD COUNT
    // =========================================================

    public Map<String, Object>
    getUnreadCount(
            Long userId
    ) {

        long count =
                notificationRepository
                        .countByUserIdAndReadFlagFalse(
                                userId
                        );

        Map<String, Object> response =
                new LinkedHashMap<>();

        response.put(
                "unreadCount",
                count
        );

        return response;
    }

    // =========================================================
    // MARK ONE AS READ
    // =========================================================

    @Transactional
    public Map<String, Object>
    markAsRead(
            Long notificationId,
            Long userId
    ) {

        Notification notification =
                notificationRepository
                        .findByIdAndUserId(
                                notificationId,
                                userId
                        )
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Notification not found"
                                        )
                        );

        notification.setReadFlag(
                true
        );

        Notification saved =
                notificationRepository
                        .save(notification);

        return toResponse(saved);
    }

    // =========================================================
    // MARK ALL AS READ
    // =========================================================

    @Transactional
    public Map<String, Object>
    markAllAsRead(
            Long userId
    ) {

        List<Notification> notifications =
                notificationRepository
                        .findByUserIdAndReadFlagFalse(
                                userId
                        );

        for (Notification notification : notifications) {
            notification.setReadFlag(
                    true
            );
        }

        notificationRepository
                .saveAll(notifications);

        Map<String, Object> response =
                new LinkedHashMap<>();

        response.put(
                "message",
                "All notifications marked as read"
        );

        response.put(
                "updatedCount",
                notifications.size()
        );

        return response;
    }

    // =========================================================
    // RESPONSE
    // =========================================================

    private Map<String, Object>
    toResponse(
            Notification notification
    ) {

        Map<String, Object> response =
                new LinkedHashMap<>();

        response.put(
                "id",
                notification.getId()
        );

        response.put(
                "type",
                notification.getType()
        );

        response.put(
                "title",
                notification.getTitle()
        );

        response.put(
                "message",
                notification.getMessage()
        );

        response.put(
                "studyGroupId",
                notification.getStudyGroupId()
        );

        response.put(
                "studySessionId",
                notification.getStudySessionId()
        );

        response.put(
                "targetUrl",
                notification.getTargetUrl()
        );

        response.put(
                "read",
                notification.isReadFlag()
        );

        response.put(
                "createdAt",
                notification.getCreatedAt()
        );

        return response;
    }
}