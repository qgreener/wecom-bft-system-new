package com.wecombft.application.command.inventory;

import java.util.Map;

public record SkuCommand(
    Long skuId,
    String skuName,
    String categoryCode,
    String skuType,
    String unit,
    Map<String, Object> specAttrs,
    Long defaultSupplierId,
    Long costPriceCent,
    Integer safetyStock,
    String status,
    String imageUrl
) {
}
