package com.wecombft.application.fulfillment.response;

import java.util.List;

public record ShipmentPage(List<ShipmentListItem> records, int pageNo, int pageSize, long total) {
}
