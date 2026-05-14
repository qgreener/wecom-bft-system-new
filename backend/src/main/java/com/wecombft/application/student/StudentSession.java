package com.wecombft.application.student;

public record StudentSession(
    long studentId,
    String studentNo,
    long userId,
    String userNo,
    String mobile,
    String nickname,
    String wxOpenid,
    String wxUnionid,
    String status,
    Long mergedToStudentId,
    String userStatus
) {
}
