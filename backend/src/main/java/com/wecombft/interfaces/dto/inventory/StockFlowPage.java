package com.wecombft.interfaces.dto.inventory;

import java.util.List;

public record StockFlowPage(List<StockFlowResponse> records, int pageNo, int pageSize, long total) {
}
