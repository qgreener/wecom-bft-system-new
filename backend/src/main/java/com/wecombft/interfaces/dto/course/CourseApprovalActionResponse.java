package com.wecombft.interfaces.dto.course;

public record CourseApprovalActionResponse(
    long approvalId,
    long courseId,
    String approvalType,
    String status,
    String courseStatus
) {
}
