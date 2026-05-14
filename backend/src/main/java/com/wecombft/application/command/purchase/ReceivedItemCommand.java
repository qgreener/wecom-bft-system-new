package com.wecombft.application.command.purchase;

public record ReceivedItemCommand(Long skuId, Integer receivedQuantity) {
}
