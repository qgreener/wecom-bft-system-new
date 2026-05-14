package com.wecombft.interfaces.dto.course;

import java.time.LocalDateTime;

public record LessonNodeItem(
    long nodeId,
    long courseId,
    Long parentNodeId,
    String nodeType,
    String title,
    String lessonType,
    LocalDateTime liveStartAt,
    LocalDateTime liveEndAt,
    String replayUrl,
    String resourceFile,
    String status,
    int sortNo
) {
}
