package com.wecombft.interfaces.dto.course;

import java.util.List;

public record CoursePage(List<CourseListItem> records, int pageNo, int pageSize, int total) {
}
