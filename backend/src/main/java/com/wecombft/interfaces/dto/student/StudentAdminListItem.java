package com.wecombft.interfaces.dto.student;

import java.time.LocalDateTime;

public record StudentAdminListItem(
    long studentId,
    String studentNo,
    Long userId,
    String mobile,
    String nickname,
    String realName,
    String status,
    Long primaryLeadId,
    Long mergedToStudentId,
    LocalDateTime createdAt
) {
}
