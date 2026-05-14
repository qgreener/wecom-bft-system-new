package com.wecombft.interfaces.dto.course;

public record CourseApprovalResponse(
    long approvalId,
    String approvalNo,
    long courseId,
    String approvalType,
    String status,
    String courseStatus
) {
}
