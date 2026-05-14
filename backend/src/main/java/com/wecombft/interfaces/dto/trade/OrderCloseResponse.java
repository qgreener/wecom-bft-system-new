package com.wecombft.interfaces.dto.trade;

import java.time.LocalDateTime;

public record OrderCloseResponse(long orderId, String orderNo, String paymentStatus, LocalDateTime closedAt, String closeReason) {
}
