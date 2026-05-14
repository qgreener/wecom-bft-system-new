package com.wecombft.interfaces.dto.course;

import java.time.LocalDateTime;

public record CourseListItem(
    long courseId,
    String courseNo,
    String courseTitle,
    String courseType,
    Long teacherUserId,
    String categoryCode,
    Long defaultTaxRuleId,
    String status,
    LocalDateTime publishedAt,
    Long minSalePriceCent
) {
}
