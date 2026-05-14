package com.wecombft.interfaces.dto.course;

public record LessonNodeResponse(
    long nodeId,
    long courseId,
    Long parentNodeId,
    String nodeType,
    String title,
    String lessonType,
    String status,
    int sortNo
) {
}
