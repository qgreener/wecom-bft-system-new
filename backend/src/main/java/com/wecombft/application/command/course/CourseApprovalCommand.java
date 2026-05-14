package com.wecombft.application.command.course;

import java.time.LocalDateTime;

public record CourseApprovalCommand(String approvalType, String submitReason, LocalDateTime deleteNoticeDeadline) {
}
