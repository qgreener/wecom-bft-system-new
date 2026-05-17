package com.wecombft.interfaces.dto.supplier;

import java.util.List;

public record SupplierPage(List<SupplierResponse> records, int pageNo, int pageSize, int total) {
}
