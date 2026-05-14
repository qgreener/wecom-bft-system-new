package com.wecombft.interfaces.dto.finance;

public record RefundCallbackResponse(String processingStatus, Long refundId, String refundNo, String status, String failureReason) {
}
