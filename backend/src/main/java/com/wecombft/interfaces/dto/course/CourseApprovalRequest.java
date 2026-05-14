package com.wecombft.interfaces.dto.course;

import com.wecombft.application.command.course.CourseApprovalCommand;
import java.time.LocalDateTime;

public record CourseApprovalRequest(
    String approvalType,
    String submitReason,
    LocalDateTime deleteNoticeDeadline
) {
    public CourseApprovalCommand toCommand() {
        return new CourseApprovalCommand(
            approvalType,
            submitReason,
            deleteNoticeDeadline);
    }
}
