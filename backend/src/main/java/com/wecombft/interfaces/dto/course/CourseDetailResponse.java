package com.wecombft.interfaces.dto.course;

import java.util.List;

public record CourseDetailResponse(
    long courseId,
    String courseNo,
    String courseTitle,
    String courseType,
    String coverUrl,
    String summary,
    String detail,
    Long teacherUserId,
    String categoryCode,
    String courseGroupQr,
    Long defaultTaxRuleId,
    String status,
    List<CourseSpecItem> specs,
    List<LessonNodeItem> lessonSummary
) {
}
