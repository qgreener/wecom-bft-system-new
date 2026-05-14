package com.wecombft.interfaces.dto.course;

import java.util.List;

public record EntitlementLessonsResponse(
    long entitlementId,
    long courseId,
    String courseGroupQr,
    List<LessonNodeItem> nodes
) {
}
