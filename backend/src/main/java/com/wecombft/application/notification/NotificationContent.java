package com.wecombft.application.notification;

public record NotificationContent(
    String sceneCode,
    String templateCode,
    String title,
    String content,
    String relatedObjectType,
    Long relatedObjectId,
    String idempotencyKey,
    Long createdBy,
    String clickUrl
) {
}
