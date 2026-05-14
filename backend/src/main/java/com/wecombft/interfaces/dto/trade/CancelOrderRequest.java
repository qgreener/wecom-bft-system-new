package com.wecombft.interfaces.dto.trade;

import com.wecombft.application.command.trade.CancelOrderCommand;

public record CancelOrderRequest(
    String closeReason
) {
    public CancelOrderCommand toCommand() {
        return new CancelOrderCommand(
            closeReason);
    }
}
