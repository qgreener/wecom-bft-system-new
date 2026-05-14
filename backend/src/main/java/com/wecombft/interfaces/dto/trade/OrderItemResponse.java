package com.wecombft.interfaces.dto.trade;

import java.util.Map;

public record OrderItemResponse(
    long orderItemId,
    int lineNo,
    long courseId,
    long specId,
    String itemName,
    int quantity,
    long unitPriceCent,
    long totalAmountCent,
    long discountAmountCent,
    long payableAmountCent,
    Long paidAmountCent,
    boolean containsPhysical,
    Long skuId,
    Long giftSkuId,
    Map<String, Object> courseSnapshot,
    Map<String, Object> specSnapshot,
    Map<String, Object> taxSnapshot
) {
}
