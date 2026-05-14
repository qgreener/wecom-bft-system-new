package com.wecombft.application.fulfillment.response;

public record ShipmentItemResponse(long itemId, long skuId, String skuNo, String skuName, String lineType, int quantity, Long stockFlowId) {
}
