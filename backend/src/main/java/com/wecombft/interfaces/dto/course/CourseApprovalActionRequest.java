package com.wecombft.interfaces.dto.course;

import com.wecombft.application.command.course.CourseApprovalActionCommand;

public record CourseApprovalActionRequest(
    String action,
    String approvalComment
) {
    public CourseApprovalActionCommand toCommand() {
        return new CourseApprovalActionCommand(
            action,
            approvalComment);
    }
}
