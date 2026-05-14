package com.wecombft.interfaces.dto.course;

import com.wecombft.application.command.course.CourseSaveCommand;
import java.time.LocalDateTime;

public record CourseSaveRequest(
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
    public CourseSaveCommand toCommand() {
        return new CourseSaveCommand(
            courseTitle,
            courseType,
            coverUrl,
            summary,
            detail,
            teacherUserId,
            categoryCode,
            courseGroupQr,
            defaultTaxRuleId,
            saleStartAt,
            saleEndAt);
    }
}
