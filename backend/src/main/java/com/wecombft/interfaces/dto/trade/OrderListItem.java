package com.wecombft.interfaces.dto.trade;

import java.time.LocalDateTime;
import java.util.Map;

public record OrderListItem(
    long orderId,
    String orderNo,
    String merchantOrderNo,
    long studentId,
    long userId,
    Long leadId,
    Map<String, Object> courseSnapshot,
    Long paidAmountCent,
    long payableAmountCent,
    String paymentStatus,
    String fulfillmentStatus,
    String refundStatus,
    String invoiceStatus,
    LocalDateTime paymentExpireAt,
    LocalDateTime serverTime,
    LocalDateTime createdAt
) {
}
