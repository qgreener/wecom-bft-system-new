package com.wecombft.application.command.purchase;

public record PurchaseItemCommand(Long skuId, Integer quantity, Long unitPriceCent) {
}
