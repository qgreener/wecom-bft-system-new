package com.wecombft.interfaces.dto.course;

public record CourseSpecResponse(
    long specId,
    String specNo,
    long courseId,
    String specName,
    long salePriceCent,
    String status,
    boolean containsPhysical
) {
}
