package com.wecombft.infrastructure.persistence.notification;

import java.time.LocalDateTime;

public record NotificationRecord(
    long id,
    String notificationNo,
    Long receiverUserId,
    String channel,
    String sceneCode,
    String templateCode,
    String title,
    String content,
    String relatedObjectType,
    Long relatedObjectId,
    String sendStatus,
    String readStatus,
    LocalDateTime createdAt
) {
}
