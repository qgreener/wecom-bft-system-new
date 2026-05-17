package com.wecombft.interfaces.dto.student;

import java.util.List;

public record StudentAdminPage(List<StudentAdminListItem> records, int pageNo, int pageSize, int total) {
}
