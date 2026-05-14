package com.wecombft.interfaces.dto.trade;

import com.wecombft.application.command.trade.OrderCreateCommand;

public record OrderCreateRequest(
    Long courseId,
    Long specId,
    Integer quantity,
    Long addressId,
    String clientRequestNo,
    String sourceChannel,
    String sourceCode,
    Long confirmedPayableAmountCent,
    String confirmToken
) {
    public OrderCreateCommand toCommand() {
        return new OrderCreateCommand(
            courseId,
            specId,
            quantity,
            addressId,
            clientRequestNo,
            sourceChannel,
            sourceCode,
            confirmedPayableAmountCent,
            confirmToken);
    }
}
