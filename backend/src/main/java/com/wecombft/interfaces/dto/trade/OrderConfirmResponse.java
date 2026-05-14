package com.wecombft.interfaces.dto.trade;

import java.time.LocalDateTime;
import java.util.Map;

public record OrderConfirmResponse(
    Map<String, Object> courseSnapshot,
    Map<String, Object> priceSnapshot,
    Map<String, Object> taxSnapshot,
    Map<String, Object> receiverSnapshot,
    long totalAmountCent,
    long discountAmountCent,
    long payableAmountCent,
    boolean containsPhysical,
    boolean stockWarning,
    String confirmToken,
    LocalDateTime serverTime
) {
}
