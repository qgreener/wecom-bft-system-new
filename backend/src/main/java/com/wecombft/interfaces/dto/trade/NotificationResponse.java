package com.wecombft.interfaces.dto.trade;

import java.time.LocalDateTime;

public record NotificationResponse(
    long notificationId,
    String notificationNo,
    String channel,
    String sceneCode,
    String title,
    String content,
    String sendStatus,
    String readStatus,
    LocalDateTime sentAt
) {
}
