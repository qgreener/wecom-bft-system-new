package com.wecombft.interfaces.dto.trade;

import com.wecombft.application.command.trade.OrderConfirmCommand;

public record OrderConfirmRequest(
    Long courseId,
    Long specId,
    Integer quantity,
    Long addressId,
    String sourceChannel,
    String sourceCode
) {
    public OrderConfirmCommand toCommand() {
        return new OrderConfirmCommand(
            courseId,
            specId,
            quantity,
            addressId,
            sourceChannel,
            sourceCode);
    }
}
