package com.wecombft.interfaces.dto.student;

public record PhoneAuthorizeResponse(
    long studentId,
    String studentNo,
    String mobile,
    String mergedFromStudentNo,
    String status,
    boolean mobileBound
) {
}
