package com.wecombft.interfaces.dto.student;

import java.time.LocalDateTime;

public record StudentAdminDetailResponse(
    long studentId,
    String studentNo,
    Long userId,
    String userNo,
    String mobile,
    String nickname,
    String realName,
    String wxOpenid,
    String wxUnionid,
    String wecomExternalUserId,
    String status,
    Long primaryLeadId,
    Long mergedToStudentId,
    String tags,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
