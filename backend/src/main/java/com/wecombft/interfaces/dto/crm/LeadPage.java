package com.wecombft.interfaces.dto.crm;

import java.util.List;

public record LeadPage(List<LeadResponse> records, int pageNo, int pageSize, int total) {
}
