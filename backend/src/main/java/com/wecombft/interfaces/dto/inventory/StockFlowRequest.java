package com.wecombft.interfaces.dto.inventory;

import com.wecombft.application.command.inventory.StockFlowCommand;

public record StockFlowRequest(
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
    public StockFlowCommand toCommand() {
        return new StockFlowCommand(
            skuId,
            direction,
            quantity,
            bizType,
            bizId,
            bizNo,
            orderId,
            shipmentId,
            purchaseId,
            remark);
    }
}
