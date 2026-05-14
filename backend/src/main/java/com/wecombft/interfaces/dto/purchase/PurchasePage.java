package com.wecombft.interfaces.dto.purchase;

import java.util.List;

public record PurchasePage(List<PurchaseSummary> records, int pageNo, int pageSize, long total) {
}
