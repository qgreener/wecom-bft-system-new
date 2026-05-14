package com.wecombft.interfaces.dto.student;

public record StudentMeResponse(
    long studentId,
    String studentNo,
    long userId,
    String mobile,
    String nickname,
    String status,
    Long mergedToStudentId
) {
}
