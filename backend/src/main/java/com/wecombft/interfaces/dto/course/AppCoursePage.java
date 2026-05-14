package com.wecombft.interfaces.dto.course;

import java.util.List;

public record AppCoursePage(List<AppCourseListItem> records, int pageNo, int pageSize, int total) {
}
