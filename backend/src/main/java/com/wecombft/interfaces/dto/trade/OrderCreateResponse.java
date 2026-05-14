package com.wecombft.interfaces.dto.trade;

import java.time.LocalDateTime;

public record OrderCreateResponse(
    long orderId,
    String orderNo,
    String merchantOrderNo,
    String paymentStatus,
    String fulfillmentStatus,
    String refundStatus,
    String invoiceStatus,
    long payableAmountCent,
    LocalDateTime paymentExpireAt,
    LocalDateTime serverTime
) {
}
