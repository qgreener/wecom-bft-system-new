package com.wecombft.application.command.course;

import java.time.LocalDateTime;

public record LessonNodeCommand(
    Long parentNodeId,
    String nodeType,
    String title,
    String lessonType,
    LocalDateTime liveStartAt,
    LocalDateTime liveEndAt,
    String replayUrl,
    String resourceFile,
    String status,
    int sortNo,
    boolean remindEnabled
) {
}
