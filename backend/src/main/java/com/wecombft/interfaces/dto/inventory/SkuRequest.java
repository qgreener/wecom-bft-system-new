package com.wecombft.interfaces.dto.inventory;

import com.wecombft.application.command.inventory.SkuCommand;
import java.util.Map;

public record SkuRequest(
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
    public SkuCommand toCommand() {
        return new SkuCommand(
            skuId,
            skuName,
            categoryCode,
            skuType,
            unit,
            specAttrs,
            defaultSupplierId,
            costPriceCent,
            safetyStock,
            status,
            imageUrl);
    }
}
