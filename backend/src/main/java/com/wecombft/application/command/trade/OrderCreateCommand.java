package com.wecombft.application.command.trade;

public record OrderCreateCommand(
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
    public OrderConfirmCommand toConfirmCommand() {
        return new OrderConfirmCommand(courseId, specId, quantity, addressId, sourceChannel, sourceCode);
    }
}
