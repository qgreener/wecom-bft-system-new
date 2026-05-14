package com.wecombft.interfaces.dto.purchase;

import java.time.LocalDateTime;

public record StockFlowResponse(
    long flowId,
    String flowNo,
    long skuId,
    String bizType,
    long bizId,
    String bizNo,
    Long orderId,
    Long shipmentId,
    Long purchaseId,
    String direction,
    int quantity,
    int beforeStock,
    int afterStock,
    Long operatorUserId,
    LocalDateTime occurredAt,
    String idempotencyKey,
    String remark
) {
}
