package com.wecombft.interfaces.dto.course;

import java.time.LocalDateTime;

public record AppCourseListItem(
    long courseId,
    String courseNo,
    String courseTitle,
    String coverUrl,
    String summary,
    String courseType,
    LocalDateTime saleStartAt,
    LocalDateTime saleEndAt,
    long minSalePriceCent,
    String status
) {
}
