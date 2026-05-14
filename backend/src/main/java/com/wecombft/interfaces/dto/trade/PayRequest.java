package com.wecombft.interfaces.dto.trade;

import com.wecombft.application.command.trade.PayCommand;

public record PayRequest(
    String paymentChannel
) {
    public PayCommand toCommand() {
        return new PayCommand(
            paymentChannel);
    }
}
