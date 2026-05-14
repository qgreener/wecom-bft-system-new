package com.wecombft.interfaces.dto.course;

import java.util.List;

public record CourseSpecsResponse(long courseId, List<CourseSpecItem> specs) {
}
