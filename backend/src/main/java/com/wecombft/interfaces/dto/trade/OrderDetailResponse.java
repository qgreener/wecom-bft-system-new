package com.wecombft.interfaces.dto.trade;

import java.util.List;
import java.time.LocalDateTime;
import java.util.Map;

public record OrderDetailResponse(
    long orderId,
    String orderNo,
    String merchantOrderNo,
    long studentId,
    long userId,
    Long leadId,
    List<OrderItemResponse> items,
    Map<String, Object> courseSnapshot,
    Map<String, Object> priceSnapshot,
    Map<String, Object> taxSnapshot,
    Map<String, Object> receiverSnapshot,
    long totalAmountCent,
    long discountAmountCent,
    long payableAmountCent,
    Long paidAmountCent,
    String paymentStatus,
    String fulfillmentStatus,
    String refundStatus,
    String invoiceStatus,
    LocalDateTime paymentExpireAt,
    LocalDateTime paidAt,
    LocalDateTime closedAt,
    String closeReason,
    List<PaymentRecordResponse> paymentRecords,
    List<EntitlementResponse> entitlements,
    List<ShipmentResponse> shipments,
    List<NotificationResponse> notifications,
    List<DocumentLinkResponse> documentLinks,
    List<AuditLogSummary> auditLogs
) {
}
