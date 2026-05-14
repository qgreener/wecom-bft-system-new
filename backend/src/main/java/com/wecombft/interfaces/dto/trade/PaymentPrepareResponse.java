package com.wecombft.interfaces.dto.trade;

import java.time.LocalDateTime;
import java.util.Map;

public record PaymentPrepareResponse(
    long orderId,
    String orderNo,
    String merchantOrderNo,
    Map<String, Object> paymentParams,
    LocalDateTime paymentExpireAt,
    LocalDateTime serverTime
) {
}
