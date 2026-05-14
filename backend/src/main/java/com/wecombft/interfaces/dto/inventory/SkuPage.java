package com.wecombft.interfaces.dto.inventory;

import java.util.List;

public record SkuPage(List<SkuResponse> records, int pageNo, int pageSize, long total) {
}
