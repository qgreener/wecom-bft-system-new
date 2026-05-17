package com.wecombft.interfaces.dto.course;

import java.util.List;

public record EntitlementAdminPage(List<EntitlementItem> records, int pageNo, int pageSize, int total) {
}
