package com.wecombft.interfaces.dto.purchase;

public record PurchaseItemResponse(long itemId, int lineNo, long skuId, String skuNo, String skuName, int quantity, long unitPriceCent, long totalAmountCent, int receivedQuantity) {
}
