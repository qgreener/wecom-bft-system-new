package com.wecombft.application.command.inventory;

public record StockFlowCommand(
    Long skuId,
    String direction,
    Integer quantity,
    String bizType,
    Long bizId,
    String bizNo,
    Long orderId,
    Long shipmentId,
    Long purchaseId,
    String remark
) {
}
