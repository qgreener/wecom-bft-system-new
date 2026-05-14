package com.wecombft.application.command.course;

import java.time.LocalDateTime;

public record CourseSaveCommand(
    String courseTitle,
    String courseType,
    String coverUrl,
    String summary,
    String detail,
    Long teacherUserId,
    String categoryCode,
    String courseGroupQr,
    Long defaultTaxRuleId,
    LocalDateTime saleStartAt,
    LocalDateTime saleEndAt
) {
}
