package com.giut.server.notification.dto;

import com.giut.server.notification.entity.Notification;

import java.time.Instant;
import java.util.Map;

public record NotificationResponse(Long id, Notification.Type type, String title, String body, String actionPath,
                                   Map<String, Object> payload, boolean read, Instant readAt, Instant createdAt) {

    public static NotificationResponse of(Notification notification, Map<String, Object> payload) {
        return new NotificationResponse(notification.getId(), notification.getNotificationType(), notification.getTitle(),
                notification.getBody(), notification.getActionPath(), payload, notification.getReadAt() != null,
                notification.getReadAt(), notification.getCreatedAt());
    }
}
