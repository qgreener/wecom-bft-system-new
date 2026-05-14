package com.wecombft.interfaces.dto.course;

import java.time.LocalDateTime;

public record CourseSaveResponse(long courseId, String courseNo, String status, LocalDateTime updatedAt) {
}
