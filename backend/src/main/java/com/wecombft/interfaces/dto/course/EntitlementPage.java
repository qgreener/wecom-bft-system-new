package com.wecombft.interfaces.dto.course;

import java.util.List;

public record EntitlementPage(List<EntitlementItem> records, int pageNo, int pageSize, int total) {
}
