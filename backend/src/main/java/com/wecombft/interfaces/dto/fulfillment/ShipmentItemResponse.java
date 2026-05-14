package com.wecombft.interfaces.dto.fulfillment;

public record ShipmentItemResponse(long itemId, long skuId, String skuNo, String skuName, String lineType, int quantity, Long stockFlowId) {
}
