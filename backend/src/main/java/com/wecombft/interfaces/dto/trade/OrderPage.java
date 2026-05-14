package com.wecombft.interfaces.dto.trade;

import java.util.List;

public record OrderPage(List<OrderListItem> records, int pageNo, int pageSize, int total) {
}
