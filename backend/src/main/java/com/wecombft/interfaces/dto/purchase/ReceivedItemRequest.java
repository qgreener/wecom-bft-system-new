package com.wecombft.interfaces.dto.purchase;

import com.wecombft.application.command.purchase.ReceivedItemCommand;

public record ReceivedItemRequest(
    Long skuId,
    Integer receivedQuantity
) {
    public ReceivedItemCommand toCommand() {
        return new ReceivedItemCommand(
            skuId,
            receivedQuantity);
    }
}
