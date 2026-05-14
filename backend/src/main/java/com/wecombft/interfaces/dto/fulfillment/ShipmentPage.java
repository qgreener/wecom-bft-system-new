package com.wecombft.interfaces.dto.fulfillment;

import java.util.List;

public record ShipmentPage(List<ShipmentListItem> records, int pageNo, int pageSize, long total) {
}
