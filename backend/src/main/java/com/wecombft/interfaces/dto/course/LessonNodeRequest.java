package com.wecombft.interfaces.dto.course;

import com.wecombft.application.command.course.LessonNodeCommand;
import java.time.LocalDateTime;

public record LessonNodeRequest(
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
    public LessonNodeCommand toCommand() {
        return new LessonNodeCommand(
            parentNodeId,
            nodeType,
            title,
            lessonType,
            liveStartAt,
            liveEndAt,
            replayUrl,
            resourceFile,
            status,
            sortNo,
            remindEnabled);
    }
}
