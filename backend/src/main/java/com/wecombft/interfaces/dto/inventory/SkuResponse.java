package com.wecombft.interfaces.dto.inventory;

import java.util.Map;

public record SkuResponse(
    long skuId,
    String skuNo,
    String skuName,
    String categoryCode,
    String skuType,
    String unit,
    Map<String, Object> specAttrs,
    Long defaultSupplierId,
    Long costPriceCent,
    int currentStock,
    int lockedStock,
    int availableStock,
    int safetyStock,
    String status,
    String imageUrl
) {
}
