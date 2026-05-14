package com.wecombft.interfaces.dto.purchase;

import com.wecombft.application.command.purchase.PurchaseItemCommand;

public record PurchaseItemRequest(
    Long skuId,
    Integer quantity,
    Long unitPriceCent
) {
    public PurchaseItemCommand toCommand() {
        return new PurchaseItemCommand(
            skuId,
            quantity,
            unitPriceCent);
    }
}
